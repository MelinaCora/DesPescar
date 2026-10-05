package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.dto.carrito.AgregarEstadiaRequest;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.ReservationDetailMapper;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingRepository;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CarritoServiceGrupoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private HotelClient hotelClient;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private PlatformTransactionManager gestor;

    private CarritoSoporte soporte;
    private CarritoService service;
    private Reservation enGrupo;

    @BeforeEach
    void setUp() {
        soporte = new CarritoSoporte(bookingRepository, RELOJ, gestor, inventario);
        service = new CarritoService(bookingRepository, soporte, hotelClient, inventario,
                new ReservationMapper(new ReservationDetailMapper(), RELOJ),
                Validation.buildDefaultValidatorFactory().getValidator(), new TransactionTemplate(gestor));
        enGrupo = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SPLIT_PAYMENT).estado(ReservationStatus.ESPERANDO_PAGADORES)
                .limiteTiempo(AHORA.plusHours(20)).build();
        EstadiaHotel e = new EstadiaHotel();
        e.setId(3L);
        e.setReservation(enGrupo);
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(new BigDecimal("900000.00"));
        enGrupo.getEstadias().add(e);
        // La base solo tiene la reserva en grupo: aparece si se pregunta por ESPERANDO_PAGADORES
        lenient().when(bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(eq(7L), any()))
                .thenAnswer(inv -> {
                    Collection<ReservationStatus> estados = inv.getArgument(1);
                    return estados.contains(ReservationStatus.ESPERANDO_PAGADORES) ? Optional.of(enGrupo) : Optional.empty();
                });
        lenient().when(bookingRepository.findIdsCarritoAbierto(eq(7L), any())).thenReturn(List.of());
        lenient().when(bookingRepository.findById(12L)).thenReturn(Optional.of(enGrupo));
        lenient().when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(enGrupo));
    }

    private static void es409EnGrupo(BookingException ex) {
        assertEquals("PAGO_EN_GRUPO_EN_CURSO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void elOrganizadorSigueViendoSuCarritoMientrasSePagaEnGrupo() {
        Optional<ReservationResponse> carrito = service.obtenerCarrito(7L);

        assertTrue(carrito.isPresent());
        assertEquals(ReservationStatus.ESPERANDO_PAGADORES, carrito.get().getEstadoGeneral());
        assertEquals(20 * 3600L, carrito.get().getSegundosRestantes());
    }

    @Test
    void otroUsuarioNoTieneCarrito() {
        assertTrue(service.obtenerCarrito(9L).isEmpty());
    }

    @Test
    void agregarUnaEstadiaConGrupoEnCursoResponde409SinLlamarAlHotel() {
        AgregarEstadiaRequest pedido = new AgregarEstadiaRequest(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 1, 2);

        es409EnGrupo(assertThrows(BookingException.class, () -> service.agregarEstadia(pedido, 7L)));

        verifyNoInteractions(hotelClient);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void noSeQuitanItemsDeUnCarritoQueSePagaEnGrupo() {
        es409EnGrupo(assertThrows(BookingException.class, () -> service.quitarEstadia(3L, 7L)));
        es409EnGrupo(assertThrows(BookingException.class, () -> service.quitarVuelo(7L)));

        assertEquals(1, enGrupo.getEstadias().size());
        verifyNoInteractions(inventario);
    }

    @Test
    void tampocoSeCreaOtroCarrito() {
        es409EnGrupo(assertThrows(BookingException.class, () -> soporte.crearCarrito(7L)));

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void abandonarUnCarritoEnGrupoPideCancelarElGrupo() {
        BookingException ex = assertThrows(BookingException.class, () -> service.abandonar(12L, 7L));

        assertEquals("USAR_CANCELACION_DEL_GRUPO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ReservationStatus.ESPERANDO_PAGADORES, enGrupo.getEstado());
        verifyNoInteractions(inventario);
    }

    @Test
    void unCarritoComunNoCuentaComoGrupoEnCurso() {
        Reservation comun = Reservation.builder().id(15L).creadorId(8L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.INICIADA)
                .limiteTiempo(AHORA.plusMinutes(10)).build();
        // Aunque un doble de test devuelva cualquier carrito, solo cuenta el que espera pagadores
        lenient().when(bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(eq(8L), any()))
                .thenReturn(Optional.of(comun));

        assertTrue(soporte.grupoEnCurso(8L).isEmpty());
        soporte.exigirSinGrupoEnCurso(8L);
    }
}
