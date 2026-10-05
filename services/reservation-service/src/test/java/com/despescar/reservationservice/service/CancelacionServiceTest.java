package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.PaymentClient;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.dto.pagos.ReembolsoReservaResponse;
import com.despescar.reservationservice.dto.reservation.response.CancelacionResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.TramoPolitica;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Cancelación de una reserva confirmada por su dueño: estados, inventario, reembolso e idempotencia. */
@ExtendWith(MockitoExtension.class)
class CancelacionServiceTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    /** 5/10/2026 15:00 en Argentina. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ARGENTINA);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private FlightClient flightClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    private CancelacionService service;
    private Reservation reserva;
    private EstadiaHotel estadia;

    @BeforeEach
    void setUp() {
        service = new CancelacionService(bookingRepository,
                new CarritoSoporte(bookingRepository, RELOJ, transactionManager, inventario), inventario, paymentClient,
                flightClient, RELOJ, new TransactionTemplate(transactionManager));
        // Vuelo de $200.000 que sale en más de 24 h (100 %) y estadía de $60.000 a menos de 72 h del check-in (50 %)
        estadia = ReembolsoCalculoTest.estadia("60000.00", LocalDate.of(2026, 10, 7), ARGENTINA.getId(),
                List.of(new TramoPolitica(24, 50), new TramoPolitica(72, 100)));
        reserva = ReembolsoCalculoTest.reserva(LocalDateTime.of(2026, 10, 7, 9, 0), estadia);
        lenient().when(bookingRepository.findById(15L)).thenReturn(Optional.of(reserva));
        lenient().when(bookingRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(reserva));
        FlightLookupResponse vuelo = new FlightLookupResponse();
        vuelo.setFlightNumber("DSC100");
        lenient().when(flightClient.getFlightByNumber(any())).thenReturn(vuelo);
        lenient().when(paymentClient.reembolsarReserva(anyLong(), any(), any()))
                .thenReturn(new ReembolsoReservaResponse(new BigDecimal("230000.00"), 0));
    }

    @Test
    void laVistaPreviaDetallaCadaItemYNoCambiaNada() {
        CancelacionResponse vista = service.vistaPrevia(15L, 7L);

        assertEquals(new BigDecimal("230000.00"), vista.reembolsoTotal());
        assertEquals("ARS", vista.moneda());
        assertEquals(ReservationStatus.CONFIRMADA, vista.estado());
        assertEquals(2, vista.detalle().size());
        assertEquals("VUELO", vista.detalle().get(0).tipo());
        assertEquals(100, vista.detalle().get(0).porcentaje());
        assertEquals("ESTADIA", vista.detalle().get(1).tipo());
        assertEquals(new BigDecimal("30000.00"), vista.detalle().get(1).monto());
        assertFalse(vista.pagoEnGrupo());
        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelarLiberaElInventarioGuardaElMontoYPideElReembolso() {
        CancelacionResponse hecho = service.cancelar(15L, 7L);

        assertEquals(ReservationStatus.CANCELADA, hecho.estado());
        assertEquals(new BigDecimal("230000.00"), hecho.reembolsoTotal());
        assertFalse(hecho.reembolsoPendiente());
        assertEquals(ReservationStatus.CANCELADA, reserva.getEstado());
        assertEquals("CANCELADA_POR_USUARIO", reserva.getMotivoCancelacion());
        assertEquals(LocalDateTime.of(2026, 10, 5, 15, 0), reserva.getCanceladaEn());
        assertEquals(new BigDecimal("230000.00"), reserva.getMontoReembolsado());
        assertEquals(new BigDecimal("200000.00"), reserva.getMontoReembolsadoVuelo());
        assertEquals(new BigDecimal("30000.00"), estadia.getMontoReembolsado());
        assertFalse(reserva.isReembolsoPendiente());
        // reserva bloqueada → asientos en la transacción → commit → retenciones, cupo y reembolso por HTTP
        InOrder orden = inOrder(bookingRepository, inventario, transactionManager, flightClient, paymentClient);
        orden.verify(bookingRepository).findByIdForUpdate(15L);
        orden.verify(inventario).liberarAsientos(reserva);
        orden.verify(transactionManager).commit(any());
        orden.verify(inventario).liberarRetenciones(reserva);
        orden.verify(flightClient).adjustSeats("DSC100", 2);
        orden.verify(paymentClient).reembolsarReserva(15L, new BigDecimal("230000.00"), "CANCELADA_POR_USUARIO");
    }

    @Test
    void cancelarDosVecesDevuelveLoMismoSinLiberarNiReembolsarDeNuevo() {
        CancelacionResponse primera = service.cancelar(15L, 7L);
        CancelacionResponse segunda = service.cancelar(15L, 7L);

        assertEquals(primera, segunda);
        assertEquals(50, segunda.detalle().get(1).porcentaje());
        verify(inventario, times(1)).liberarAsientos(reserva);
        verify(inventario, times(1)).liberarRetenciones(reserva);
        verify(flightClient, times(1)).adjustSeats(any(), anyInt());
        verify(paymentClient, times(1)).reembolsarReserva(anyLong(), any(), any());
    }

    @Test
    void siPaymentServiceFallaLaReservaQuedaCanceladaConElReembolsoPendienteYElSchedulerLoReintenta() {
        when(paymentClient.reembolsarReserva(anyLong(), any(), any()))
                .thenThrow(new BookingException("PAYMENT_SERVICE_UNAVAILABLE", "caído", HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(new ReembolsoReservaResponse(new BigDecimal("230000.00"), 0));

        CancelacionResponse hecho = service.cancelar(15L, 7L);

        assertEquals(ReservationStatus.CANCELADA, hecho.estado());
        assertTrue(hecho.reembolsoPendiente());
        assertTrue(reserva.isReembolsoPendiente());

        when(bookingRepository.idsConReembolsoPendiente()).thenReturn(List.of(15L));
        service.pedirReembolsosPendientes();

        assertFalse(reserva.isReembolsoPendiente());
        verify(paymentClient, times(2)).reembolsarReserva(15L, new BigDecimal("230000.00"), "CANCELADA_POR_USUARIO");
    }

    @Test
    void sinNadaParaDevolverNoLlamaAPaymentService() {
        reserva.setSalidaVuelo(LocalDateTime.of(2026, 10, 6, 9, 0));
        estadia.setPoliticaCancelacion(new java.util.ArrayList<>());

        CancelacionResponse hecho = service.cancelar(15L, 7L);

        assertEquals(new BigDecimal("0.00"), hecho.reembolsoTotal());
        assertFalse(hecho.reembolsoPendiente());
        verify(paymentClient, never()).reembolsarReserva(anyLong(), any(), any());
    }

    @Test
    void noSeCancelaDespuesDeLaPrimeraSalidaOCheckIn() {
        reserva.setSalidaVuelo(LocalDateTime.of(2026, 10, 5, 14, 0));

        BookingException ex = assertThrows(BookingException.class, () -> service.cancelar(15L, 7L));

        assertEquals("CANCELACION_FUERA_DE_PLAZO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        assertEquals("CANCELACION_FUERA_DE_PLAZO",
                assertThrows(BookingException.class, () -> service.vistaPrevia(15L, 7L)).getCodigo());
    }

    @Test
    void soloElCreadorYSoloUnaReservaConfirmada() {
        assertEquals("ACCESO_DENEGADO", assertThrows(BookingException.class, () -> service.cancelar(15L, 8L)).getCodigo());

        reserva.setEstado(ReservationStatus.PENDIENTE_PAGO);
        BookingException ex = assertThrows(BookingException.class, () -> service.cancelar(15L, 7L));
        assertEquals("RESERVA_NO_CANCELABLE", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());

        reserva.setEstado(ReservationStatus.CANCELADA);
        reserva.setMotivoCancelacion("PAGO_EN_GRUPO_VENCIDO");
        assertEquals("RESERVA_NO_CANCELABLE", assertThrows(BookingException.class, () -> service.cancelar(15L, 7L)).getCodigo());
        assertNull(reserva.getCanceladaEn());
    }

    @Test
    void unaReservaPagadaEnGrupoLoAvisa() {
        reserva.setTipoPago(PaymentType.SPLIT_PAYMENT);
        assertTrue(service.vistaPrevia(15L, 7L).pagoEnGrupo());
    }
}
