package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.PackageClient;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.dto.reservation.request.BookingInitRequest;
import com.despescar.reservationservice.dto.reservation.response.BookingInitResponse;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.Reservation;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class BookingServiceInitTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    // 15:00 en Buenos Aires: si el código usara la zona de la JVM, los límites saldrían corridos
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final LocalDateTime SALIDA = LocalDateTime.of(2026, 10, 19, 8, 0);
    private static final UUID VUELO = UUID.randomUUID();
    private static final UUID TARIFA = UUID.randomUUID();

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

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, detailRepository, messagingTemplate,
                new ReservationMapper(new ReservationDetailMapper(), RELOJ), flightClient, packageClient,
                new CarritoSoporte(bookingRepository, RELOJ, org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class), inventario), new PrecioVuelo(flightClient), inventario,
                new org.springframework.transaction.support.TransactionTemplate(org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class)));
    }

    private void vueloDisponible() {
        FlightLookupResponse v = new FlightLookupResponse();
        v.setId(VUELO);
        v.setFlightNumber("AR1004");
        v.setPrice(new BigDecimal("160000.00"));
        v.setStatus("SCHEDULED");
        v.setAvailableSeats(150);
        v.setDepartureTime(SALIDA);
        v.setFares(new ArrayList<>(List.of(PrecioVueloTest.tarifa(TARIFA, "Light", "ARS", "0"))));
        when(flightClient.getFlightByNumber(VUELO)).thenReturn(v);
    }

    private void carritoActual(Reservation carrito) {
        when(bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(eq(7L), any()))
                .thenReturn(Optional.ofNullable(carrito));
        if (carrito != null) {
            org.mockito.Mockito.lenient().when(bookingRepository.findByIdForUpdate(carrito.getId())).thenReturn(Optional.of(carrito));
        }
    }

    private void guardarAsignaId() {
        java.util.concurrent.atomic.AtomicReference<Reservation> creado = new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.stubbing.Answer<Reservation> asignaId = inv -> {
            Reservation r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(12L);
                creado.set(r);
            }
            return r;
        };
        org.mockito.Mockito.lenient().when(bookingRepository.findByIdForUpdate(12L)).thenAnswer(inv -> Optional.ofNullable(creado.get()));
        when(bookingRepository.save(any(Reservation.class))).thenAnswer(asignaId);
        when(bookingRepository.saveAndFlush(any(Reservation.class))).thenAnswer(asignaId); // el carrito nuevo se crea con flush
    }

    private static BookingInitRequest pedido(PaymentType tipo) {
        BookingInitRequest p = new BookingInitRequest();
        p.setFlightIds(new ArrayList<>(List.of(VUELO)));
        p.setBaggageIds(new ArrayList<>(List.of(TARIFA)));
        p.setCantidadPasajeros(2);
        p.setPaymentType(tipo);
        p.setHotelId(UUID.randomUUID()); // en desuso: se ignora
        return p;
    }

    private static Reservation carrito(Long id, ReservationStatus estado, LocalDateTime limite) {
        return Reservation.builder().id(id).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(estado).limiteTiempo(limite).build();
    }

    @Test
    void sinCarritoCreaUnoConElPrecioCalculadoEnElServidor() {
        carritoActual(null);
        vueloDisponible();
        guardarAsignaId();

        BookingInitResponse r = service.initializeBooking(pedido(PaymentType.SINGLE_PAYMENT), null, 7L);

        assertEquals(12L, r.getBookingId());
        assertEquals("INICIADA", r.getStatus());
        org.mockito.ArgumentCaptor<Reservation> captor = org.mockito.ArgumentCaptor.forClass(Reservation.class);
        verify(bookingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Reservation guardado = captor.getValue();
        assertEquals(new BigDecimal("160000.00"), guardado.getPrecioVueloPorPasajero());
        assertEquals(2, guardado.getCantidadPasajeros());
        assertEquals("Light", guardado.getTarifasVuelo());
        assertEquals(SALIDA, guardado.getSalidaVuelo());
        assertEquals(List.of(VUELO), guardado.getFlightIds());
        assertEquals(List.of(TARIFA), guardado.getBaggageIds());
        assertEquals(AHORA.plusMinutes(15), guardado.getLimiteTiempo());
        assertNull(guardado.getHotelId());
        verify(inventario).alinearBloqueos(guardado);
    }

    @Test
    void elHotelIdDelPedidoSeIgnora() {
        Reservation deHotel = carrito(30L, ReservationStatus.INICIADA, AHORA.plusMinutes(5));
        deHotel.setHotelId(null);
        carritoActual(deHotel);
        vueloDisponible();
        when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        BookingInitRequest p = pedido(PaymentType.SINGLE_PAYMENT);
        assertEquals(true, p.getHotelId() != null);

        service.initializeBooking(p, null, 7L);

        assertNull(deHotel.getHotelId());
    }

    @Test
    void alSumarElVueloAlineaLosBloqueosDeAsientosConElCarrito() {
        Reservation deHotel = carrito(30L, ReservationStatus.INICIADA, AHORA.plusMinutes(5));
        carritoActual(deHotel);
        vueloDisponible();
        when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.initializeBooking(pedido(PaymentType.SINGLE_PAYMENT), null, 7L);

        verify(inventario).alinearBloqueos(deHotel);
    }

    @Test
    void conUnCarritoDeHotelLeSumaElVueloSinCambiarElLimite() {
        Reservation deHotel = carrito(30L, ReservationStatus.PENDIENTE_PAGO, AHORA.plusMinutes(5));
        carritoActual(deHotel);
        vueloDisponible();
        when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingInitResponse r = service.initializeBooking(pedido(PaymentType.SINGLE_PAYMENT), null, 7L);

        assertEquals(30L, r.getBookingId());
        assertEquals(ReservationStatus.INICIADA, deHotel.getEstado()); // faltan los pasajeros (D11)
        assertEquals(AHORA.plusMinutes(5), deHotel.getLimiteTiempo());
        assertEquals(2, deHotel.getCantidadPasajeros());
    }

    @Test
    void siElCarritoYaTieneVueloResponde409SinConsultarVuelos() {
        Reservation conVuelo = carrito(30L, ReservationStatus.INICIADA, AHORA.plusMinutes(5));
        conVuelo.getFlightIds().add(UUID.randomUUID());
        carritoActual(conVuelo);

        BookingException ex = assertThrows(BookingException.class,
                () -> service.initializeBooking(pedido(PaymentType.SINGLE_PAYMENT), null, 7L));

        assertEquals("CARRITO_YA_TIENE_VUELO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(flightClient, never()).getFlightByNumber(any());
    }

    @Test
    void unCarritoVencidoNoCuentaYSeCreaOtro() {
        carritoActual(carrito(30L, ReservationStatus.INICIADA, AHORA.minusMinutes(1)));
        vueloDisponible();
        guardarAsignaId();

        assertEquals(12L, service.initializeBooking(pedido(PaymentType.SINGLE_PAYMENT), null, 7L).getBookingId());
    }

    @Test
    void elPagoDivididoNoEstaDisponible() {
        BookingException ex = assertThrows(BookingException.class,
                () -> service.initializeBooking(pedido(PaymentType.SPLIT_PAYMENT), null, 7L));

        assertEquals("PAGO_DIVIDIDO_NO_DISPONIBLE", ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(bookingRepository, flightClient);
    }

    @Test
    void leerUnaReservaVencidaNoLaCancela() {
        Reservation vencida = carrito(5L, ReservationStatus.INICIADA, AHORA.minusMinutes(1));
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(vencida));

        ReservationResponse r = service.obtenerReserva(5L, 7L);

        assertEquals(ReservationStatus.INICIADA, r.getEstadoGeneral());
        assertEquals(0L, r.getSegundosRestantes());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void unaReservaAjenaNoSeLee() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(carrito(5L, ReservationStatus.INICIADA, AHORA)));

        BookingException ex = assertThrows(BookingException.class, () -> service.obtenerReserva(5L, 9L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void siOtroPedidoLeSumoUnVueloMientrasSeCotizabaResponde409SinGuardar() {
        Reservation leido = carrito(30L, ReservationStatus.INICIADA, AHORA.plusMinutes(5));
        carritoActual(leido);
        Reservation enLaBase = carrito(30L, ReservationStatus.INICIADA, AHORA.plusMinutes(5));
        enLaBase.getFlightIds().add(UUID.randomUUID());
        when(bookingRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(enLaBase));
        vueloDisponible();

        BookingException ex = assertThrows(BookingException.class,
                () -> service.initializeBooking(pedido(PaymentType.SINGLE_PAYMENT), null, 7L));

        assertEquals("CARRITO_YA_TIENE_VUELO", ex.getCodigo());
        verify(bookingRepository, never()).save(any());
        verify(inventario, never()).alinearBloqueos(any());
    }

    @Test
    void cotizaElVueloAntesDeBloquearElCarrito() {
        Reservation deHotel = carrito(30L, ReservationStatus.INICIADA, AHORA.plusMinutes(5));
        carritoActual(deHotel);
        vueloDisponible();
        when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.initializeBooking(pedido(PaymentType.SINGLE_PAYMENT), null, 7L);

        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(flightClient, bookingRepository, inventario);
        orden.verify(flightClient).getFlightByNumber(VUELO);
        orden.verify(bookingRepository).findByIdForUpdate(30L);
        orden.verify(bookingRepository).save(deHotel);
        orden.verify(inventario).alinearBloqueos(deHotel);
    }

    @Test
    void conUnPagoEnGrupoEnCursoNoSeArmaOtroCarritoNiSeCotiza() {
        carritoActual(carrito(12L, ReservationStatus.ESPERANDO_PAGADORES, AHORA.plusHours(20)));

        BookingException ex = assertThrows(BookingException.class,
                () -> service.initializeBooking(pedido(PaymentType.SINGLE_PAYMENT), null, 7L));

        assertEquals("PAGO_EN_GRUPO_EN_CURSO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(flightClient);
    }
}
