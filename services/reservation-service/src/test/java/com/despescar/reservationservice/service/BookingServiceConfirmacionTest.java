package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.PackageClient;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.dto.reservation.request.PaymentConfirmationRequest;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class BookingServiceConfirmacionTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA); // 15:00 ART
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final UUID VUELO = UUID.randomUUID();
    private static final BigDecimal TOTAL = new BigDecimal("820000.00");

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
    private ReservationDetail pasajero;
    private EstadiaHotel estadia;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, detailRepository, messagingTemplate,
                new ReservationMapper(new ReservationDetailMapper(), RELOJ), flightClient, packageClient,
                new CarritoSoporte(bookingRepository, RELOJ, transactionManager, inventario), new PrecioVuelo(flightClient), inventario,
                new TransactionTemplate(transactionManager));
        reserva = carrito();
        pasajero = reserva.getDetalles().get(0);
        estadia = reserva.getEstadias().get(0);
        lenient().when(bookingRepository.findById(12L)).thenReturn(Optional.of(reserva));
        lenient().when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(reserva));
        lenient().when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Carrito con un pasajero (240.000) y una estadía (580.000), datos completos y en hora. */
    private static Reservation carrito() {
        Reservation r = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(1)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.PENDIENTE_PAGO)
                .limiteTiempo(AHORA.plusMinutes(10)).flightIds(new ArrayList<>(List.of(VUELO)))
                .baggageIds(new ArrayList<>(List.of(UUID.randomUUID())))
                .precioVueloPorPasajero(new BigDecimal("240000.00")).tarifasVuelo("Light").build();
        r.getDetalles().add(ReservationDetail.builder().reservation(r).outboundSeatNumber("1A").passengerName("Ana Pérez")
                .passengerDni("30111222").payerUserId(7L).priceCharged(new BigDecimal("240000.00"))
                .paymentStatus(PaymentStatus.PENDIENTE).build());
        r.getEstadias().add(estadia(r, 3L, new BigDecimal("580000.00")));
        return r;
    }

    private static EstadiaHotel estadia(Reservation r, Long id, BigDecimal precio) {
        EstadiaHotel e = new EstadiaHotel();
        e.setId(id);
        e.setReservation(r);
        e.setHotelId(UUID.randomUUID());
        e.setTipoHabitacionId(UUID.randomUUID());
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(precio);
        e.setTitularNombre("Ana Pérez");
        e.setTitularDni("30111222");
        e.setTitularTelefono("1155555555");
        return e;
    }

    private static PaymentConfirmationRequest pedido(Long pagador, BigDecimal monto) {
        return pedido(pagador, monto, "MOCK-1");
    }

    private static PaymentConfirmationRequest pedido(Long pagador, BigDecimal monto, String token) {
        PaymentConfirmationRequest p = new PaymentConfirmationRequest();
        p.setPagadorId(pagador);
        p.setTokenPago(token);
        p.setMonto(monto);
        return p;
    }

    private void vueloConNumero() {
        FlightLookupResponse v = new FlightLookupResponse();
        v.setId(VUELO);
        v.setFlightNumber("AR1004");
        when(flightClient.getFlightByNumber(VUELO)).thenReturn(v);
    }

    /** Simula el rescate de InventarioCarrito: la retención liberada se reemplaza por una nueva. */
    private UUID rescataLaRetencion(Reservation leida) {
        UUID nueva = UUID.randomUUID();
        doAnswer(inv -> {
            leida.getEstadias().get(0).setRetencionId(nueva);
            return true;
        }).when(inventario).confirmarEstadias(leida);
        return nueva;
    }

    @Test
    void confirmaPrimeroLasEstadiasFueraDeTransaccionYDespuesLosAsientos() {
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(true);
        vueloConNumero();

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("CONFIRMADA", r.estado());
        assertNull(r.motivo());
        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        assertEquals("MOCK-1", reserva.getTokenPagoConfirmacion());
        assertEquals(PaymentStatus.PAGADO, pasajero.getPaymentStatus());
        assertEquals(PaymentStatus.PAGADO, estadia.getEstadoPago());
        // lectura (tx corta) → estadías por HTTP sin transacción → asientos en otra tx corta, con la
        // reserva bloqueada → cupo de vuelo
        InOrder orden = inOrder(transactionManager, inventario, bookingRepository, flightClient);
        orden.verify(transactionManager).getTransaction(any());
        orden.verify(transactionManager).commit(any());
        orden.verify(inventario).confirmarEstadias(reserva);
        orden.verify(transactionManager).getTransaction(any());
        orden.verify(bookingRepository).findByIdForUpdate(12L);
        orden.verify(inventario).confirmarAsientos(reserva);
        orden.verify(transactionManager).commit(any());
        orden.verify(flightClient).adjustSeats("AR1004", -1);
    }

    @Test
    void elMismoPagoReenviadoRespondeConfirmadaSinTocarNada() {
        reserva.setEstado(ReservationStatus.CONFIRMADA);
        reserva.setTokenPagoConfirmacion("MOCK-1");

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("CONFIRMADA", r.estado());
        verifyNoInteractions(inventario, flightClient);
    }

    @Test
    void otroPagoSobreUnaReservaConfirmadaEsDuplicado() {
        reserva.setEstado(ReservationStatus.CONFIRMADA);
        reserva.setTokenPagoConfirmacion("MOCK-1");

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL, "MOCK-2"));

        assertEquals("RECHAZADA", r.estado());
        assertEquals("PAGO_DUPLICADO", r.motivo());
        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        assertEquals("MOCK-1", reserva.getTokenPagoConfirmacion());
        verifyNoInteractions(inventario, flightClient);
    }

    @Test
    void elPagadorTieneQueSerElCreador() {
        BookingException ex = assertThrows(BookingException.class, () -> service.confirmarPago(12L, pedido(9L, TOTAL)));

        assertEquals("PAGADOR_INVALIDO", ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(inventario);
    }

    @Test
    void unMontoDistintoDelTotalSeRechazaSinTocarLaReserva() {
        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, new BigDecimal("240000.00")));

        assertEquals("RECHAZADA", r.estado());
        assertEquals("MONTO_NO_COINCIDE", r.motivo());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, reserva.getEstado());
        verifyNoInteractions(inventario);
    }

    @Test
    void elMontoSeComparaExactoSinRedondearLoQueLlega() {
        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, new BigDecimal("819999.995")));

        assertEquals("MONTO_NO_COINCIDE", r.motivo());
        verifyNoInteractions(inventario);
    }

    @Test
    void elMontoConOtraEscalaPeroIgualValorCoincide() {
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(true);
        vueloConNumero();

        assertEquals("CONFIRMADA", service.confirmarPago(12L, pedido(7L, new BigDecimal("820000"))).estado());
    }

    @Test
    void conDatosIncompletosSeRechaza() {
        estadia.setTitularNombre(null);

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("RECHAZADA", r.estado());
        assertEquals("DATOS_INCOMPLETOS", r.motivo());
        verifyNoInteractions(inventario);
    }

    @Test
    void siLosAsientosYaNoEstanSeCancelaYLasRetencionesSeLiberanDespuesDelCommit() {
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(false);

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("CANCELADA", r.estado());
        assertEquals("SIN_DISPONIBILIDAD", r.motivo());
        assertEquals(ReservationStatus.CANCELADA, reserva.getEstado());
        assertEquals("SIN_DISPONIBILIDAD", reserva.getMotivoCancelacion());
        assertEquals(PaymentStatus.CANCELADO, pasajero.getPaymentStatus());
        // La cancelación y los asientos van en la misma transacción que vio la falta de lugar; la
        // compensación remota (hotel-service) recién después del commit
        InOrder orden = inOrder(inventario, transactionManager);
        orden.verify(inventario).confirmarEstadias(reserva);
        orden.verify(inventario).confirmarAsientos(reserva);
        orden.verify(inventario).liberarAsientos(reserva);
        orden.verify(transactionManager).commit(any());
        orden.verify(inventario).liberarRetenciones(reserva);
        verify(flightClient, never()).adjustSeats(anyString(), anyInt());
    }

    @Test
    void unPagoTardioSobreUnaReservaExpiradaRetieneDeNuevoYSinLugarCancelaConSuMotivo() {
        reserva.setEstado(ReservationStatus.EXPIRADA);
        reserva.setLimiteTiempo(AHORA.minusMinutes(20));
        when(inventario.retenerYConfirmarEstadias(reserva)).thenReturn(false);

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("CANCELADA", r.estado());
        assertEquals("PAGO_TARDIO_SIN_DISPONIBILIDAD", r.motivo());
        assertEquals(ReservationStatus.CANCELADA, reserva.getEstado());
        assertEquals("PAGO_TARDIO_SIN_DISPONIBILIDAD", reserva.getMotivoCancelacion());
        assertEquals(PaymentStatus.CANCELADO, pasajero.getPaymentStatus());
        // Las retenciones viejas de una EXPIRADA son del scheduler: no se confirman
        verify(inventario, never()).confirmarEstadias(any());
        verify(inventario, never()).confirmarAsientos(any());
        // Sus asientos ya los soltó el scheduler: liberarlos otra vez podría tocar los de otro carrito
        verify(inventario, never()).liberarAsientos(any());
    }

    @Test
    void unPagoTardioSobreUnaReservaExpiradaConLugarSeConfirma() {
        reserva.setEstado(ReservationStatus.EXPIRADA);
        reserva.setLimiteTiempo(AHORA.minusMinutes(20));
        when(inventario.retenerYConfirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(true);
        vueloConNumero();

        assertEquals("CONFIRMADA", service.confirmarPago(12L, pedido(7L, TOTAL)).estado());
        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        assertEquals("MOCK-1", reserva.getTokenPagoConfirmacion());
    }

    @Test
    void unPagoTardioAunNoExpiradoConLugarSeConfirma() {
        reserva.setLimiteTiempo(AHORA.minusMinutes(1));
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(true);
        vueloConNumero();

        assertEquals("CONFIRMADA", service.confirmarPago(12L, pedido(7L, TOTAL)).estado());
        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
    }

    @Test
    void unPagoTardioAunNoExpiradoSinLugarEnElHotelCancelaComoTardio() {
        reserva.setLimiteTiempo(AHORA.minusMinutes(1));
        when(inventario.confirmarEstadias(reserva)).thenReturn(false);

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("PAGO_TARDIO_SIN_DISPONIBILIDAD", r.motivo());
        verify(inventario, never()).confirmarAsientos(any());
        InOrder orden = inOrder(inventario, transactionManager);
        orden.verify(inventario).liberarAsientos(reserva);
        orden.verify(transactionManager).commit(any());
        orden.verify(inventario).liberarRetenciones(reserva);
    }

    @Test
    void siElSchedulerLaExpiroDuranteLaLlamadaAlHotelSeReintentaReteniendoDeNuevo() {
        Reservation expirada = carrito();
        expirada.setEstado(ReservationStatus.EXPIRADA);
        expirada.setLimiteTiempo(AHORA.minusMinutes(1));
        Reservation leida = carrito();
        leida.getEstadias().get(0).setRetencionId(estadia.getRetencionId());
        leida.setLimiteTiempo(AHORA.minusMinutes(1));
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(leida), Optional.of(expirada));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(expirada));
        UUID rescatada = rescataLaRetencion(leida);
        when(inventario.retenerYConfirmarEstadias(expirada)).thenReturn(true);
        when(inventario.confirmarAsientos(expirada)).thenReturn(true);
        vueloConNumero();

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("CONFIRMADA", r.estado());
        assertEquals(ReservationStatus.CONFIRMADA, expirada.getEstado());
        // La retención que el primer intento creó al rescatar se suelta; las viejas las libera el scheduler
        verify(inventario).liberarRetencion(rescatada);
        verify(inventario, never()).liberarRetencion(estadia.getRetencionId());
        verify(inventario, times(1)).confirmarAsientos(any());
    }

    @Test
    void siElCarritoCambioDuranteLaLlamadaAlHotelSeReevaluaYNoSeConfirma() {
        Reservation leida = carrito();
        Reservation cambiada = carrito();
        cambiada.getEstadias().add(estadia(cambiada, 4L, new BigDecimal("100000.00")));
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(leida), Optional.of(cambiada));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(cambiada));
        UUID rescatada = rescataLaRetencion(leida);

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("RECHAZADA", r.estado());
        assertEquals("MONTO_NO_COINCIDE", r.motivo());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, cambiada.getEstado());
        verify(inventario, never()).confirmarAsientos(any());
        verify(inventario).liberarRetencion(rescatada);
        verify(inventario, never()).liberarRetenciones(any());
    }

    @Test
    void siOtroPagoLaConfirmoDuranteLaLlamadaAlHotelEsDuplicadoYNoSueltaLasRetencionesDeLaReserva() {
        Reservation leida = carrito();
        UUID original = leida.getEstadias().get(0).getRetencionId();
        Reservation confirmada = carrito();
        confirmada.setEstado(ReservationStatus.CONFIRMADA);
        confirmada.setTokenPagoConfirmacion("MOCK-2");
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(leida));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(confirmada));
        when(inventario.confirmarEstadias(leida)).thenReturn(true);

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("RECHAZADA", r.estado());
        assertEquals("PAGO_DUPLICADO", r.motivo());
        assertEquals("MOCK-2", confirmada.getTokenPagoConfirmacion());
        verify(inventario, never()).liberarRetencion(original);
        verify(inventario, never()).liberarRetenciones(any());
        verify(inventario, never()).confirmarAsientos(any());
    }

    @Test
    void siElMismoPagoLlegoDosVecesALaPorLaSegundaRespondeConfirmadaYSueltaLoQueRescato() {
        Reservation leida = carrito();
        Reservation confirmada = carrito();
        confirmada.setEstado(ReservationStatus.CONFIRMADA);
        confirmada.setTokenPagoConfirmacion("MOCK-1");
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(leida));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(confirmada));
        UUID rescatada = rescataLaRetencion(leida);

        assertEquals("CONFIRMADA", service.confirmarPago(12L, pedido(7L, TOTAL)).estado());

        verify(inventario).liberarRetencion(rescatada);
        verify(inventario, never()).confirmarAsientos(any());
        verifyNoInteractions(flightClient);
    }

    @Test
    void siLaTransaccionDeAsientosFallaSeSueltanLasRetencionesRescatadasYSePropaga() {
        Reservation leida = carrito();
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(leida));
        when(bookingRepository.findByIdForUpdate(12L)).thenThrow(new CannotAcquireLockException("deadlock"));
        UUID rescatada = rescataLaRetencion(leida);

        assertThrows(CannotAcquireLockException.class, () -> service.confirmarPago(12L, pedido(7L, TOTAL)));

        verify(inventario).liberarRetencion(rescatada);
    }

    @Test
    void siElCarritoSeGuardaConLasRetencionesRescatadas() {
        Reservation leida = carrito();
        Reservation enBase = carrito();
        enBase.getEstadias().get(0).setRetencionId(leida.getEstadias().get(0).getRetencionId());
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(leida));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(enBase));
        UUID rescatada = rescataLaRetencion(leida);
        when(inventario.confirmarAsientos(enBase)).thenReturn(true);
        vueloConNumero();

        assertEquals("CONFIRMADA", service.confirmarPago(12L, pedido(7L, TOTAL)).estado());

        assertEquals(rescatada, enBase.getEstadias().get(0).getRetencionId());
        verify(inventario, never()).liberarRetencion(any());
    }

    @Test
    void unCarritoAbandonadoSeInformaCancelado() {
        reserva.setEstado(ReservationStatus.CANCELADA);
        reserva.setMotivoCancelacion("ABANDONADA");

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("CANCELADA", r.estado());
        assertEquals("RESERVA_CANCELADA", r.motivo());
        verifyNoInteractions(inventario);
    }

    @Test
    void reintentarDespuesDeUnPagoTardioRepiteElMotivo() {
        reserva.setEstado(ReservationStatus.CANCELADA);
        reserva.setMotivoCancelacion("PAGO_TARDIO_SIN_DISPONIBILIDAD");

        assertEquals("PAGO_TARDIO_SIN_DISPONIBILIDAD", service.confirmarPago(12L, pedido(7L, TOTAL)).motivo());
    }

    @Test
    void unaCaidaDelHotelSePropagaSinTocarAsientos() {
        when(inventario.confirmarEstadias(reserva))
                .thenThrow(new BookingException("HOTEL_SERVICE_UNAVAILABLE", "caido", HttpStatus.SERVICE_UNAVAILABLE));

        BookingException ex = assertThrows(BookingException.class, () -> service.confirmarPago(12L, pedido(7L, TOTAL)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(inventario, never()).confirmarAsientos(any());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, reserva.getEstado());
    }

    @Test
    void siFlightServiceNoDescuentaElCupoLaReservaQuedaConfirmada() {
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(true);
        vueloConNumero();
        doThrow(new BookingException("FLIGHT_SEATS_ADJUST_ERROR", "caido", HttpStatus.BAD_GATEWAY))
                .when(flightClient).adjustSeats("AR1004", -1);

        assertEquals("CONFIRMADA", service.confirmarPago(12L, pedido(7L, TOTAL)).estado());
    }

    @Test
    void unaReservaInexistenteResponde404() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        BookingException ex = assertThrows(BookingException.class, () -> service.confirmarPago(99L, pedido(7L, TOTAL)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void pagarUnaReservaAjenaResponde403() {
        BookingException ex = assertThrows(BookingException.class, () -> service.procesarPago(12L, 9L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void pagarUnCarritoVencidoResponde410SinCancelarlo() {
        reserva.setLimiteTiempo(AHORA.minusMinutes(1));

        BookingException ex = assertThrows(BookingException.class, () -> service.procesarPago(12L, 7L));

        assertEquals("CARRITO_EXPIRADO", ex.getCodigo());
        assertEquals(HttpStatus.GONE, ex.getStatus());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, reserva.getEstado());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void elAvisoPorWebSocketSaleDespuesDelCommit() {
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(true);
        vueloConNumero();

        service.confirmarPago(12L, pedido(7L, TOTAL));

        InOrder orden = inOrder(inventario, transactionManager, messagingTemplate);
        orden.verify(inventario).confirmarAsientos(reserva);
        orden.verify(transactionManager).commit(any());
        orden.verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/reserva/12"), any(Object.class));
    }

    @Test
    void unAvisoQueFallaNoCambiaLaRespuesta() {
        when(inventario.confirmarEstadias(reserva)).thenReturn(true);
        when(inventario.confirmarAsientos(reserva)).thenReturn(true);
        vueloConNumero();
        doThrow(new IllegalStateException("broker caído")).when(messagingTemplate)
                .convertAndSend(org.mockito.ArgumentMatchers.anyString(), any(Object.class));

        assertEquals("CONFIRMADA", service.confirmarPago(12L, pedido(7L, TOTAL)).estado());
    }

    @Test
    void unIntentoQueTerminaDespuesDeQueOtroConfirmoNoSueltaNadaDeEse() {
        // A confirmó (mismo pago, reintento tras un timeout); B se queda sin lugar en el hotel porque
        // revalidó tarde: no se cancela ni se suelta ninguna retención de A
        Reservation leida = carrito();
        Reservation confirmadaPorA = carrito();
        confirmadaPorA.setEstado(ReservationStatus.CONFIRMADA);
        confirmadaPorA.setTokenPagoConfirmacion("MOCK-1");
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(leida));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(confirmadaPorA));
        when(inventario.confirmarEstadias(leida)).thenReturn(false);

        ConfirmacionPagoResponse r = service.confirmarPago(12L, pedido(7L, TOTAL));

        assertEquals("CONFIRMADA", r.estado());
        assertEquals(ReservationStatus.CONFIRMADA, confirmadaPorA.getEstado());
        verify(inventario, never()).liberarRetenciones(any());
        verify(inventario, never()).liberarRetencion(any());
        verify(inventario, never()).liberarAsientos(any());
    }

    @Test
    void siCambioElNombreDelTitularSeVuelveAConfirmarConElNuevo() {
        Reservation leida = carrito();
        Reservation renombrada = carrito();
        renombrada.getEstadias().get(0).setId(leida.getEstadias().get(0).getId());
        renombrada.getEstadias().get(0).setTitularNombre("Beatriz Gómez");
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(leida), Optional.of(renombrada));
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(renombrada));
        when(inventario.confirmarEstadias(leida)).thenReturn(true);
        when(inventario.confirmarEstadias(renombrada)).thenReturn(true);
        when(inventario.confirmarAsientos(renombrada)).thenReturn(true);
        vueloConNumero();

        assertEquals("CONFIRMADA", service.confirmarPago(12L, pedido(7L, TOTAL)).estado());

        // El hotel recibe el titular vigente, no el que se leyó antes del cambio
        verify(inventario).confirmarEstadias(renombrada);
        verify(inventario, times(1)).confirmarAsientos(any());
    }
}
