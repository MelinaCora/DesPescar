package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.despescar.reservationservice.dto.passengers.request.PassengerAssignationRequest;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.SeatRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PassengerServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA); // 15:00 ART
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final UUID VUELO = UUID.randomUUID();
    private static final UUID OTRO_VUELO = UUID.randomUUID();
    private static final UUID TARIFA = UUID.randomUUID();

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private InventarioCarrito inventario;

    private PassengerService service;
    private Reservation carrito;

    @BeforeEach
    void setUp() {
        service = new PassengerService(bookingRepository, seatRepository, new CarritoSoporte(bookingRepository, RELOJ), inventario);
        carrito = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(2)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.INICIADA)
                .limiteTiempo(AHORA.plusMinutes(10))
                .flightIds(new ArrayList<>(List.of(VUELO))).baggageIds(new ArrayList<>(List.of(TARIFA)))
                .precioVueloPorPasajero(new BigDecimal("240000.00")).tarifasVuelo("Light").build();
        lenient().when(bookingRepository.findById(12L)).thenReturn(Optional.of(carrito));
        lenient().when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Seat asiento(String numero, UUID vuelo, String estado, Long bloqueadoPor) {
        Seat s = new Seat();
        s.setSeatUuid(UUID.randomUUID());
        s.setFlightId(vuelo);
        s.setNumberSeat(numero);
        s.setStatusSeat(estado);
        s.setBlockedByUserId(bloqueadoPor);
        s.setBloqueadoHasta(AHORA.plusMinutes(10));
        lenient().when(seatRepository.findById(s.getSeatUuid())).thenReturn(Optional.of(s));
        lenient().when(seatRepository.findByIdForUpdate(s.getSeatUuid())).thenReturn(Optional.of(s));
        lenient().when(seatRepository.findByFlightIdAndNumberSeatForUpdate(vuelo, numero)).thenReturn(Optional.of(s));
        return s;
    }

    private Seat mio(String numero) {
        return asiento(numero, VUELO, "RESERVADO_TEMPORAL", 7L);
    }

    private static PassengerAssignationRequest pedido(Seat... asientos) {
        List<PassengerAssignationRequest.PassengerItemDTO> pasajeros = new ArrayList<>();
        for (int i = 0; i < asientos.length; i++) {
            PassengerAssignationRequest.PassengerItemDTO p = new PassengerAssignationRequest.PassengerItemDTO();
            p.setNombreCompleto("Pasajero " + i);
            p.setDniPasaporte("3011122" + i);
            p.setAsientoIda(asientos[i].getSeatUuid());
            p.setTarifaId(UUID.randomUUID()); // lo que mande el cliente se ignora
            p.setTarifaNombre("Premium");
            pasajeros.add(p);
        }
        PassengerAssignationRequest pedido = new PassengerAssignationRequest();
        pedido.setPasajeros(pasajeros);
        return pedido;
    }

    private BookingException falla(PassengerAssignationRequest pedido, Long usuario) {
        return assertThrows(BookingException.class, () -> service.assignPassengersToSeats(12L, pedido, usuario));
    }

    @Test
    void cargaLosPasajerosConElPrecioCongeladoDelCarrito() {
        service.assignPassengersToSeats(12L, pedido(mio("1A"), mio("1B")), 7L);

        assertEquals(2, carrito.getDetalles().size());
        for (ReservationDetail d : carrito.getDetalles()) {
            assertEquals(new BigDecimal("240000.00"), d.getPriceCharged());
            assertEquals(TARIFA, d.getFareId());
            assertEquals("Light", d.getFareName());
            assertEquals("ARS", d.getFareCurrency());
            assertEquals(7L, d.getPayerUserId());
            assertEquals(PaymentStatus.PENDIENTE, d.getPaymentStatus());
            assertEquals(carrito, d.getReservation());
        }
        assertEquals(List.of("1A", "1B"), carrito.getDetalles().stream().map(ReservationDetail::getOutboundSeatNumber).toList());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, carrito.getEstado());
        verify(inventario).alinearBloqueos(carrito);
    }

    @Test
    void conUnaEstadiaSinTitularElCarritoSigueIniciado() {
        EstadiaHotel e = new EstadiaHotel();
        e.setReservation(carrito);
        e.setPrecioTotal(new BigDecimal("580000.00"));
        carrito.getEstadias().add(e);

        service.assignPassengersToSeats(12L, pedido(mio("1A"), mio("1B")), 7L);

        assertEquals(ReservationStatus.INICIADA, carrito.getEstado());
    }

    @Test
    void unSegundoPutReemplazaLosPasajerosSinDuplicarYSueltaElAsientoQueYaNoSeUsa() {
        Seat a = mio("1A");
        Seat b = mio("1B");
        Seat c = mio("1C");
        service.assignPassengersToSeats(12L, pedido(a, b), 7L);

        service.assignPassengersToSeats(12L, pedido(a, c), 7L);

        assertEquals(2, carrito.getDetalles().size());
        assertEquals(List.of("1A", "1C"), carrito.getDetalles().stream().map(ReservationDetail::getOutboundSeatNumber).toList());
        assertEquals("DISPONIBLE", b.getStatusSeat());
        assertNull(b.getBlockedByUserId());
        assertEquals("RESERVADO_TEMPORAL", a.getStatusSeat());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, carrito.getEstado());
    }

    @Test
    void unAsientoDeOtroVueloEsInvalido() {
        BookingException ex = falla(pedido(mio("1A"), asiento("1B", OTRO_VUELO, "RESERVADO_TEMPORAL", 7L)), 7L);

        assertEquals("ASIENTO_INVALIDO", ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(0, carrito.getDetalles().size());
    }

    @Test
    void elMismoAsientoParaDosPasajerosEsInvalido() {
        Seat a = mio("1A");

        assertEquals("ASIENTO_INVALIDO", falla(pedido(a, a), 7L).getCodigo());
    }

    @Test
    void unAsientoQueNoBloqueoElUsuarioResponde409() {
        BookingException ex = falla(pedido(mio("1A"), asiento("1B", VUELO, "RESERVADO_TEMPORAL", 9L)), 7L);

        assertEquals("ASIENTO_NO_BLOQUEADO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void unAsientoYaPagadoNoSeReutiliza() {
        assertEquals("ASIENTO_NO_BLOQUEADO", falla(pedido(mio("1A"), asiento("1B", VUELO, "OCUPADO", 7L)), 7L).getCodigo());
    }

    @Test
    void laCantidadTieneQueCoincidir() {
        assertEquals("CANTIDAD_INVALIDA", falla(pedido(mio("1A")), 7L).getCodigo());
    }

    @Test
    void unCarritoVencidoResponde410() {
        carrito.setLimiteTiempo(AHORA.minusSeconds(1));

        BookingException ex = falla(pedido(mio("1A"), mio("1B")), 7L);

        assertEquals("CARRITO_EXPIRADO", ex.getCodigo());
        assertEquals(HttpStatus.GONE, ex.getStatus());
        assertEquals(ReservationStatus.INICIADA, carrito.getEstado());
    }

    @Test
    void sinVueloNoHayPasajeros() {
        carrito.setFlightIds(new ArrayList<>());

        assertEquals("SIN_VUELO", falla(pedido(mio("1A"), mio("1B")), 7L).getCodigo());
    }

    @Test
    void unaReservaPagadaNoSeModifica() {
        carrito.setEstado(ReservationStatus.CONFIRMADA);

        BookingException ex = falla(pedido(mio("1A"), mio("1B")), 7L);

        assertEquals("ESTADO_INVALIDO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void soloElCreadorCargaPasajeros() {
        assertEquals(HttpStatus.FORBIDDEN, falla(pedido(mio("1A"), mio("1B")), 9L).getStatus());
    }

    @Test
    void elOrdenDeBloqueoNoDependeDelOrdenDelPedido() {
        Seat a = mio("1A");
        Seat b = mio("1B");
        Seat c = mio("1C");
        service.assignPassengersToSeats(12L, pedido(c, a), 7L);
        org.mockito.InOrder orden = inOrder(seatRepository);
        orden.verify(seatRepository).findByIdForUpdate(a.getSeatUuid());
        orden.verify(seatRepository).findByIdForUpdate(c.getSeatUuid());

        org.mockito.Mockito.clearInvocations(seatRepository);
        carrito.getDetalles().clear();
        service.assignPassengersToSeats(12L, pedido(a, c), 7L);
        orden = inOrder(seatRepository);
        orden.verify(seatRepository).findByIdForUpdate(a.getSeatUuid());
        orden.verify(seatRepository).findByIdForUpdate(c.getSeatUuid());
        assertEquals("RESERVADO_TEMPORAL", b.getStatusSeat());
    }

    @Test
    void losAsientosPedidosYLosASoltarSeBloqueanEnUnSoloOrden() {
        Seat a = mio("1A");
        Seat b = mio("1B");
        Seat c = mio("1C");
        service.assignPassengersToSeats(12L, pedido(a, c), 7L);
        org.mockito.Mockito.clearInvocations(seatRepository);

        service.assignPassengersToSeats(12L, pedido(a, b), 7L); // suelta 1C, que ordena después de 1B

        org.mockito.InOrder orden = inOrder(seatRepository);
        orden.verify(seatRepository).findByIdForUpdate(a.getSeatUuid());
        orden.verify(seatRepository).findByIdForUpdate(b.getSeatUuid());
        orden.verify(seatRepository).findByFlightIdAndNumberSeatForUpdate(VUELO, "1C");
    }

    @Test
    void alSoltarUnAsientoElMapaSeAvisa() {
        Seat a = mio("1A");
        Seat b = mio("1B");
        Seat c = mio("1C");
        service.assignPassengersToSeats(12L, pedido(a, b), 7L);

        service.assignPassengersToSeats(12L, pedido(a, c), 7L);

        verify(inventario).avisar(b);
        verify(inventario, never()).avisar(a);
    }

    @Test
    void unPutRepetidoConElMismoAsientoLoConservaSinSoltarlo() {
        Seat a = mio("1A");
        Seat b = mio("1B");
        service.assignPassengersToSeats(12L, pedido(a, b), 7L);

        service.assignPassengersToSeats(12L, pedido(a, b), 7L);

        assertEquals(2, carrito.getDetalles().size());
        assertEquals("RESERVADO_TEMPORAL", a.getStatusSeat());
        assertEquals(7L, a.getBlockedByUserId());
        verify(inventario, never()).avisar(any());
    }

    @Test
    void unCarritoPendienteDePagoSigueAceptandoCambiosDePasajeros() {
        carrito.setEstado(ReservationStatus.PENDIENTE_PAGO);

        service.assignPassengersToSeats(12L, pedido(mio("1A"), mio("1B")), 7L);

        assertEquals(2, carrito.getDetalles().size());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, carrito.getEstado());
    }
}
