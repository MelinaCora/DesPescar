package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.dto.grupo.GrupoResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.GrupoMapper;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class GrupoPagoServiceIniciarTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA); // 15:00 ART
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final Instant LIMITE_ANTERIOR = Instant.parse("2026-10-05T18:10:00Z");
    private static final Instant PLAZO = Instant.parse("2026-10-06T18:00:00Z");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private GrupoPagoRepository grupoRepository;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private GrupoCierre cierre;

    private GrupoPagoService service;
    private Reservation reserva;

    @BeforeEach
    void setUp() {
        service = new GrupoPagoService(bookingRepository, grupoRepository,
                new CarritoSoporte(bookingRepository, RELOJ, transactionManager, inventario), inventario, new GrupoMapper(RELOJ),
                new TokenEnlace(), new TransactionTemplate(transactionManager), cierre);
        reserva = carrito(new BigDecimal("900000.00"));
        lenient().when(bookingRepository.findById(12L)).thenReturn(Optional.of(reserva));
        lenient().when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(reserva));
        lenient().when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(grupoRepository.findByReservation_Id(12L)).thenReturn(Optional.empty());
        lenient().when(grupoRepository.saveAndFlush(any(GrupoPago.class))).thenAnswer(inv -> {
            GrupoPago g = inv.getArgument(0);
            g.setId(30L);
            return g;
        });
    }

    private static Reservation carrito(BigDecimal precioEstadia) {
        Reservation r = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.PENDIENTE_PAGO)
                .limiteTiempo(AHORA.plusMinutes(10)).build();
        EstadiaHotel e = new EstadiaHotel();
        e.setId(3L);
        e.setReservation(r);
        e.setHotelNombre("Sheraton Córdoba");
        e.setCiudad("Córdoba");
        e.setTipoHabitacionNombre("Doble");
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setCantidadHabitaciones(2);
        e.setHuespedes(3);
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(precioEstadia);
        e.setTitularNombre("Ana Pérez");
        e.setTitularDni("30111222");
        e.setTitularTelefono("1155555555");
        r.getEstadias().add(e);
        return r;
    }

    @Test
    void empiezaElGrupoConPartesIgualesYElOrganizadorEnLaPrimera() {
        GrupoResponse grupo = service.iniciar(12L, 3, 7L);

        assertEquals(EstadoGrupo.ABIERTO, grupo.estado());
        assertEquals(AHORA.plusHours(24), grupo.venceEn());
        assertEquals(3, grupo.cantidadPartes());
        assertEquals(new BigDecimal("300000.00"), grupo.partes().get(2).monto());
        assertEquals(EstadoParte.TOMADA, grupo.partes().get(0).estado());
        assertTrue(grupo.partes().get(0).esMia());
        assertTrue(grupo.partes().get(0).esOrganizador());
        assertEquals(EstadoParte.LIBRE, grupo.partes().get(1).estado());
        assertTrue(grupo.soyOrganizador());
        assertEquals(1, grupo.miParte());
        assertEquals(43, grupo.enlaceToken().length());
        assertTrue(grupo.puedeEditarMontos());
        assertEquals(ReservationStatus.ESPERANDO_PAGADORES, reserva.getEstado());
        assertEquals(PaymentType.SPLIT_PAYMENT, reserva.getTipoPago());
        assertEquals(AHORA.plusHours(24), reserva.getLimiteTiempo());
        // retenciones por HTTP fuera de la transacción → transacción que alinea asientos y guarda
        InOrder orden = inOrder(inventario, transactionManager, grupoRepository);
        orden.verify(transactionManager).commit(any());
        orden.verify(inventario).cambiarVencimientoRetenciones(reserva, PLAZO, LIMITE_ANTERIOR);
        orden.verify(transactionManager).getTransaction(any());
        orden.verify(inventario).alinearBloqueos(reserva);
        orden.verify(grupoRepository).saveAndFlush(any(GrupoPago.class));
        orden.verify(transactionManager).commit(any());
        verify(inventario, never()).volverAlVencimiento(any(Reservation.class), any());
    }

    @Test
    void elGrupoGuardadoTieneTokenPlazoYFechas() {
        service.iniciar(12L, 2, 7L);

        ArgumentCaptor<GrupoPago> captor = ArgumentCaptor.forClass(GrupoPago.class);
        verify(grupoRepository).saveAndFlush(captor.capture());
        GrupoPago g = captor.getValue();
        assertEquals(7L, g.getOrganizadorId());
        assertEquals(reserva, g.getReservation());
        assertEquals(AHORA, g.getCreadoEn());
        assertEquals(AHORA, g.getActualizadoEn());
        assertTrue(TokenEnlace.formatoValido(g.getTokenEnlace()));
        assertEquals(2, g.getPartes().size());
    }

    @Test
    void soloElCreadorPuedeDividir() {
        BookingException ex = assertThrows(BookingException.class, () -> service.iniciar(12L, 3, 9L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(inventario);
    }

    @Test
    void unCarritoSinDatosCompletosNoSeDivide() {
        reserva.setEstado(ReservationStatus.INICIADA);

        BookingException ex = assertThrows(BookingException.class, () -> service.iniciar(12L, 3, 7L));

        assertEquals("ESTADO_INVALIDO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(inventario);
    }

    @Test
    void unCarritoVencidoResponde410() {
        reserva.setLimiteTiempo(AHORA.minusSeconds(1));

        BookingException ex = assertThrows(BookingException.class, () -> service.iniciar(12L, 3, 7L));

        assertEquals("CARRITO_EXPIRADO", ex.getCodigo());
        assertEquals(HttpStatus.GONE, ex.getStatus());
    }

    @Test
    void siYaTieneGrupoResponde409() {
        when(grupoRepository.findByReservation_Id(12L)).thenReturn(Optional.of(new GrupoPago()));

        assertEquals("GRUPO_YA_EXISTE",
                assertThrows(BookingException.class, () -> service.iniciar(12L, 3, 7L)).getCodigo());
        verifyNoInteractions(inventario);
    }

    @Test
    void unaCantidadInvalidaNoLlamaAlHotel() {
        assertEquals("CANTIDAD_PARTES_INVALIDA",
                assertThrows(BookingException.class, () -> service.iniciar(12L, 11, 7L)).getCodigo());
        verifyNoInteractions(inventario);
    }

    @Test
    void siElHotelNoPuedeAlargarLasRetencionesNoSeCreaNada() {
        doThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL", "sin lugar", HttpStatus.CONFLICT))
                .when(inventario).cambiarVencimientoRetenciones(any(), any(), any());

        BookingException ex = assertThrows(BookingException.class, () -> service.iniciar(12L, 3, 7L));

        assertEquals("SIN_DISPONIBILIDAD_HOTEL", ex.getCodigo());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, reserva.getEstado());
        verify(grupoRepository, never()).saveAndFlush(any());
    }

    @Test
    void siElCarritoCambioMientrasSeAlargabaSeCompensa() {
        Reservation otra = carrito(new BigDecimal("950000.00"));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(otra));

        BookingException ex = assertThrows(BookingException.class, () -> service.iniciar(12L, 3, 7L));

        assertEquals("CARRITO_CAMBIO", ex.getCodigo());
        verify(inventario).volverAlVencimiento(reserva, LIMITE_ANTERIOR);
        verify(grupoRepository, never()).saveAndFlush(any());
    }

    @Test
    void siOtroPedidoCreoElGrupoPrimeroNoSeTocanSusRetenciones() {
        when(grupoRepository.saveAndFlush(any(GrupoPago.class))).thenThrow(new DataIntegrityViolationException("uk"));
        when(grupoRepository.findByReservation_Id(12L))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(new GrupoPago()));

        BookingException ex = assertThrows(BookingException.class, () -> service.iniciar(12L, 3, 7L));

        assertEquals("GRUPO_YA_EXISTE", ex.getCodigo());
        verify(inventario, never()).volverAlVencimiento(any(Reservation.class), any());
    }

    @Test
    void sinEstadiasNoHayLlamadasAlHotelPeroIgualSeCreaElGrupo() {
        reserva.getEstadias().clear();
        reserva.setFlightIds(new java.util.ArrayList<>(java.util.List.of(UUID.randomUUID())));
        reserva.setCantidadPasajeros(1);
        reserva.setPrecioVueloPorPasajero(new BigDecimal("240000.00"));

        GrupoResponse grupo = service.iniciar(12L, 2, 7L);

        assertEquals(new BigDecimal("120000.00"), grupo.partes().get(1).monto());
        assertNull(grupo.motivoCierre());
    }
}
