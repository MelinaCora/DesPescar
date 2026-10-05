package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.PackageClient;
import com.despescar.reservationservice.dto.reservation.request.PaymentConfirmationRequest;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.mapper.ReservationDetailMapper;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.BookingRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class BookingServiceGrupoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final BigDecimal TOTAL = new BigDecimal("900000.00");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingDetailRepository detailRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private FlightClient flightClient;
    @Mock
    private PackageClient packageClient;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BookingService service;
    private Reservation reserva;
    private EstadiaHotel estadia;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, detailRepository, messagingTemplate,
                new ReservationMapper(new ReservationDetailMapper(), RELOJ), flightClient, packageClient,
                new CarritoSoporte(bookingRepository, RELOJ, transactionManager, inventario), new PrecioVuelo(flightClient), inventario,
                new TransactionTemplate(transactionManager));
        reserva = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SPLIT_PAYMENT).estado(ReservationStatus.ESPERANDO_PAGADORES)
                .limiteTiempo(AHORA.plusHours(3)).build();
        estadia = new EstadiaHotel();
        estadia.setId(3L);
        estadia.setReservation(reserva);
        estadia.setHotelId(UUID.randomUUID());
        estadia.setTipoHabitacionId(UUID.randomUUID());
        estadia.setCheckIn(LocalDate.of(2026, 11, 10));
        estadia.setCheckOut(LocalDate.of(2026, 11, 12));
        estadia.setRetencionId(UUID.randomUUID());
        estadia.setPrecioTotal(TOTAL);
        estadia.setTitularNombre("Ana Pérez");
        estadia.setTitularDni("30111222");
        estadia.setTitularTelefono("1155555555");
        reserva.getEstadias().add(estadia);
        lenient().when(bookingRepository.findById(12L)).thenReturn(Optional.of(reserva));
        lenient().when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(reserva));
        lenient().when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void laUltimaParteConfirmaLaReservaConElTokenDelGrupo() {
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(true);

        ConfirmacionPagoResponse r = service.confirmarReservaPagada(12L, "GRUPO-30");

        assertEquals("CONFIRMADA", r.estado());
        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        assertEquals("GRUPO-30", reserva.getTokenPagoConfirmacion());
        assertEquals(PaymentStatus.PAGADO, estadia.getEstadoPago());
    }

    @Test
    void unaReservaYaConfirmadaNoSeVuelveATocar() {
        reserva.setEstado(ReservationStatus.CONFIRMADA);

        assertEquals("CONFIRMADA", service.confirmarReservaPagada(12L, "GRUPO-30").estado());
        verifyNoInteractions(inventario);
    }

    @Test
    void sinLugarEnLosAsientosSeCancelaYSeLiberanLasRetenciones() {
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(false);

        ConfirmacionPagoResponse r = service.confirmarReservaPagada(12L, "GRUPO-30");

        assertEquals("CANCELADA", r.estado());
        assertEquals("SIN_DISPONIBILIDAD", r.motivo());
        assertEquals(ReservationStatus.CANCELADA, reserva.getEstado());
        verify(inventario).liberarRetenciones(reserva);
    }

    @Test
    void confirmarDespuesDelPlazoSinLugarEsPagoTardio() {
        reserva.setLimiteTiempo(AHORA.minusMinutes(1));
        when(inventario.confirmarEstadias(reserva)).thenReturn(false);

        assertEquals("PAGO_TARDIO_SIN_DISPONIBILIDAD", service.confirmarReservaPagada(12L, "GRUPO-30").motivo());
    }

    @Test
    void unPagoDeUnSoloPagadorSobreUnaReservaDivididaSeRechazaSiempre() {
        PaymentConfirmationRequest pedido = new PaymentConfirmationRequest();
        pedido.setPagadorId(7L);
        pedido.setTokenPago("MOCK-viejo");
        pedido.setMonto(TOTAL);

        ConfirmacionPagoResponse abierta = service.confirmarPago(12L, pedido);
        reserva.setEstado(ReservationStatus.CONFIRMADA);
        reserva.setTokenPagoConfirmacion("GRUPO-30");
        ConfirmacionPagoResponse confirmada = service.confirmarPago(12L, pedido);

        assertEquals("RECHAZADA", abierta.estado());
        assertEquals("PAGO_EN_GRUPO", abierta.motivo());
        assertEquals("PAGO_EN_GRUPO", confirmada.motivo());
        verifyNoInteractions(inventario);
    }

    @Test
    void unaReservaCanceladaPorMontoRespondeEseMismoMotivo() {
        reserva.setEstado(ReservationStatus.CANCELADA);
        reserva.setMotivoCancelacion("MONTO_NO_COINCIDE");

        ConfirmacionPagoResponse r = service.confirmarReservaPagada(12L, "GRUPO-30");

        assertEquals("CANCELADA", r.estado());
        assertEquals("MONTO_NO_COINCIDE", r.motivo());
    }

    @Test
    void unaConfirmacionEnCursoNoConfirmaSiElGrupoSeCanceloPorNoPoderConfirmar() {
        Reservation cancelada = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SPLIT_PAYMENT).estado(ReservationStatus.CANCELADA)
                .motivoCancelacion(GrupoCierre.MOTIVO_SIN_CONFIRMAR).limiteTiempo(AHORA.plusHours(3)).build();
        cancelada.getEstadias().add(estadia);
        // se leyó ESPERANDO_PAGADORES; mientras se llamaba al hotel el plazo de confirmación la canceló
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(reserva), Optional.of(cancelada));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(cancelada));
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);

        ConfirmacionPagoResponse r = service.confirmarReservaPagada(12L, "GRUPO-30");

        assertEquals("CANCELADA", r.estado());
        assertEquals("CONFIRMACION_FALLIDA", r.motivo());
        assertEquals(ReservationStatus.CANCELADA, cancelada.getEstado());
        verify(inventario, org.mockito.Mockito.never()).confirmarAsientos(any());
    }
}
