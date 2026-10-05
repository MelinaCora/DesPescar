package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.dto.carrito.AgregarEstadiaRequest;
import com.despescar.reservationservice.dto.carrito.TitularRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.dto.hotel.TramoHotelDto;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.PaymentStatus;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA); // 15:00 ART
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final Instant VENCE = Instant.parse("2026-10-05T18:10:00Z");
    private static final UUID HOTEL = UUID.randomUUID();
    private static final UUID TIPO = UUID.randomUUID();
    private static final UUID VUELO = UUID.randomUUID();

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private HotelClient hotelClient;
    @Mock
    private InventarioCarrito inventario;

    private PlatformTransactionManager gestor;
    private CarritoService service;
    private Reservation carrito;

    @BeforeEach
    void setUp() {
        gestor = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        service = new CarritoService(bookingRepository, new CarritoSoporte(bookingRepository, RELOJ, gestor, inventario), hotelClient,
                inventario, new ReservationMapper(new ReservationDetailMapper(), RELOJ),
                Validation.buildDefaultValidatorFactory().getValidator(), new TransactionTemplate(gestor));
        carrito = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.INICIADA)
                .limiteTiempo(AHORA.plusMinutes(10)).build();
        lenient().when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bookingRepository.saveAndFlush(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bookingRepository.findById(12L)).thenReturn(Optional.of(carrito));
        lenient().when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(carrito));
        lenient().when(inventario.vencimiento(any())).thenReturn(VENCE);
    }

    private void conCarritoActivo(Reservation activo) {
        lenient().when(bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(eq(7L), any())).thenReturn(Optional.ofNullable(activo));
        lenient().when(bookingRepository.findIdsCarritoAbierto(eq(7L), any()))
                .thenReturn(activo == null ? List.of() : List.of(activo.getId()));
    }

    /** La misma reserva 12 tal como quedó en la base después de que otro pedido la confirmara. */
    private Reservation confirmadaEnLaBase() {
        Reservation c = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.CONFIRMADA)
                .limiteTiempo(AHORA.plusMinutes(10)).build();
        EstadiaHotel e = new EstadiaHotel();
        e.setId(3L);
        e.setReservation(c);
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(new BigDecimal("580000.00"));
        e.setTitularNombre("Ana Pérez");
        e.setEstadoPago(PaymentStatus.PAGADO);
        c.getEstadias().add(e);
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(c));
        return c;
    }

    private static AgregarEstadiaRequest pedido() {
        return new AgregarEstadiaRequest(HOTEL, TIPO, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 2, 3);
    }

    private static RetencionHotelResponse retencion(UUID id, String moneda) {
        RetencionHotelResponse r = new RetencionHotelResponse();
        r.setRetencionId(id);
        r.setHotelId(HOTEL);
        r.setHotelNombre("Sheraton Córdoba");
        r.setCiudad("Córdoba");
        r.setTipoHabitacionId(TIPO);
        r.setTipoHabitacionNombre("Doble");
        r.setCheckIn(LocalDate.of(2026, 11, 10));
        r.setCheckOut(LocalDate.of(2026, 11, 12));
        r.setNoches(2);
        r.setCantidad(2);
        r.setHuespedes(3);
        r.setPrecioTotal(new BigDecimal("580000"));
        r.setMoneda(moneda);
        r.setHoraCheckIn(LocalTime.of(14, 0));
        r.setZonaHoraria("America/Argentina/Buenos_Aires");
        r.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoHotelDto(48, 100), new TramoHotelDto(0, 0))));
        r.setEstado("RETENIDA");
        r.setExpiraEn(VENCE);
        return r;
    }

    private EstadiaHotel estadia(Long id, String titular) {
        EstadiaHotel e = new EstadiaHotel();
        e.setId(id);
        e.setReservation(carrito);
        e.setHotelId(HOTEL);
        e.setTipoHabitacionId(TIPO);
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(new BigDecimal("580000.00"));
        e.setMoneda("ARS");
        e.setTitularNombre(titular);
        e.setTitularDni(titular == null ? null : "30111222");
        e.setTitularTelefono(titular == null ? null : "+54 11 5555-5555");
        carrito.getEstadias().add(e);
        return e;
    }

    private void conVueloYPasajeros() {
        carrito.getFlightIds().add(VUELO);
        carrito.setBaggageIds(new ArrayList<>(List.of(UUID.randomUUID())));
        carrito.setCantidadPasajeros(1);
        carrito.setPrecioVueloPorPasajero(new BigDecimal("240000.00"));
        carrito.setTarifasVuelo("Light");
        carrito.setSalidaVuelo(LocalDateTime.of(2026, 10, 19, 8, 0));
        carrito.getDetalles().add(ReservationDetail.builder().reservation(carrito).outboundSeatNumber("1A")
                .passengerName("Ana").passengerDni("30111222").payerUserId(7L)
                .priceCharged(new BigDecimal("240000.00")).paymentStatus(PaymentStatus.PENDIENTE).build());
    }

    @Test
    void sinCarritoLoCreaYRetieneHastaElVencimientoDelCarrito() {
        conCarritoActivo(null);
        AtomicReference<Reservation> creado = new AtomicReference<>();
        when(bookingRepository.saveAndFlush(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(40L);
            creado.set(r);
            return r;
        });
        when(bookingRepository.findByIdForUpdate(40L)).thenAnswer(inv -> Optional.of(creado.get()));
        UUID ret = UUID.randomUUID();
        when(hotelClient.crearRetencion(any())).thenReturn(retencion(ret, "ARS"));

        ReservationResponse r = service.agregarEstadia(pedido(), 7L);

        ArgumentCaptor<RetencionHotelRequest> captor = ArgumentCaptor.forClass(RetencionHotelRequest.class);
        verify(hotelClient).crearRetencion(captor.capture());
        assertEquals(40L, captor.getValue().reservaId());
        assertEquals(7L, captor.getValue().usuarioId());
        assertEquals(2, captor.getValue().cantidad());
        assertEquals(VENCE, captor.getValue().expiraEn());
        assertEquals(40L, r.getIdCarrito());
        assertEquals(ReservationStatus.INICIADA, r.getEstadoGeneral()); // falta el titular
        assertEquals(1, r.getEstadias().size());
        ReservationResponse.EstadiaDTO e = r.getEstadias().get(0);
        assertEquals(new BigDecimal("580000.00"), e.getPrecioTotal());
        assertEquals("Sheraton Córdoba", e.getHotelNombre());
        assertEquals(2, e.getCantidadHabitaciones());
        assertEquals(2, e.getPoliticaCancelacion().size());
        assertEquals(new BigDecimal("580000.00"), r.getMontoTotal());
    }

    @Test
    void unCarritoListoParaPagarVuelveAIniciadaAlSumarUnaEstadia() {
        conVueloYPasajeros();
        carrito.setEstado(ReservationStatus.PENDIENTE_PAGO);
        conCarritoActivo(carrito);
        when(hotelClient.crearRetencion(any())).thenReturn(retencion(UUID.randomUUID(), "ARS"));

        ReservationResponse r = service.agregarEstadia(pedido(), 7L);

        assertEquals(12L, r.getIdCarrito());
        assertEquals(ReservationStatus.INICIADA, carrito.getEstado());
        assertEquals(new BigDecimal("820000.00"), r.getMontoTotal());
        assertEquals(2, r.getCantidadItems());
    }

    @Test
    void sinLugarEnElHotelPropagaElErrorSinTocarElCarrito() {
        conCarritoActivo(carrito);
        when(hotelClient.crearRetencion(any())).thenThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL",
                "No quedan habitaciones de ese tipo para esas fechas.", HttpStatus.CONFLICT));

        BookingException ex = assertThrows(BookingException.class, () -> service.agregarEstadia(pedido(), 7L));

        assertEquals("SIN_DISPONIBILIDAD_HOTEL", ex.getCodigo());
        assertTrue(carrito.getEstadias().isEmpty());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaRetencionEnOtraMonedaSeDevuelve() {
        conCarritoActivo(carrito);
        UUID ret = UUID.randomUUID();
        when(hotelClient.crearRetencion(any())).thenReturn(retencion(ret, "USD"));

        BookingException ex = assertThrows(BookingException.class, () -> service.agregarEstadia(pedido(), 7L));

        assertEquals("MONEDA_NO_SOPORTADA", ex.getCodigo());
        verify(inventario).liberarRetencion(ret);
    }

    @Test
    void quitarUnaEstadiaLiberaSuRetencion() {
        conVueloYPasajeros();
        EstadiaHotel e = estadia(3L, null);
        conCarritoActivo(carrito);

        Optional<ReservationResponse> r = service.quitarEstadia(3L, 7L);

        assertTrue(r.isPresent());
        assertTrue(carrito.getEstadias().isEmpty());
        verify(inventario).liberarRetencion(e.getRetencionId());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, carrito.getEstado()); // solo queda el vuelo con pasajeros
    }

    @Test
    void quitarElUltimoItemCierraElCarrito() {
        estadia(3L, null);
        conCarritoActivo(carrito);

        assertTrue(service.quitarEstadia(3L, 7L).isEmpty());

        assertEquals(ReservationStatus.CANCELADA, carrito.getEstado());
        assertEquals("CARRITO_VACIO", carrito.getMotivoCancelacion());
    }

    @Test
    void unaEstadiaQueNoEstaEnElCarritoResponde404() {
        estadia(3L, null);
        conCarritoActivo(carrito);

        BookingException ex = assertThrows(BookingException.class, () -> service.quitarEstadia(99L, 7L));

        assertEquals("ESTADIA_NO_ENCONTRADA", ex.getCodigo());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void sinCarritoActivoNoHayNadaQueQuitar() {
        conCarritoActivo(null);

        assertEquals("CARRITO_NO_ENCONTRADO", assertThrows(BookingException.class, () -> service.quitarVuelo(7L)).getCodigo());
    }

    @Test
    void quitarElVueloLiberaAsientosYLimpiaLaParteDeVuelo() {
        conVueloYPasajeros();
        estadia(3L, "Ana Pérez");
        conCarritoActivo(carrito);

        Optional<ReservationResponse> r = service.quitarVuelo(7L);

        verify(inventario).liberarAsientos(any(Reservation.class));
        assertTrue(r.isPresent());
        assertNull(r.get().getVuelo());
        assertTrue(carrito.getFlightIds().isEmpty());
        assertTrue(carrito.getDetalles().isEmpty());
        assertEquals(0, carrito.getCantidadPasajeros());
        assertNull(carrito.getPrecioVueloPorPasajero());
        assertEquals(ReservationStatus.PENDIENTE_PAGO, carrito.getEstado()); // la estadía tiene titular
        assertEquals(new BigDecimal("580000.00"), r.get().getMontoTotal());
    }

    @Test
    void quitarElVueloDeUnCarritoSinVueloResponde404() {
        estadia(3L, null);
        conCarritoActivo(carrito);

        assertEquals("SIN_VUELO", assertThrows(BookingException.class, () -> service.quitarVuelo(7L)).getCodigo());
    }

    @Test
    void conLosTitularesCompletosElCarritoQuedaListoParaPagar() {
        estadia(3L, null);
        estadia(4L, null);

        ReservationResponse r = service.cargarTitulares(12L, List.of(
                new TitularRequest(3L, " Ana Pérez ", "30111222", "+54 11 5555-5555"),
                new TitularRequest(4L, "Luis Gómez", "28999111", "+54 11 4444-4444")), 7L);

        assertEquals(ReservationStatus.PENDIENTE_PAGO, r.getEstadoGeneral());
        assertTrue(r.getDatosCompletos());
        assertEquals("Ana Pérez", carrito.getEstadias().get(0).getTitularNombre());
        assertEquals("28999111", carrito.getEstadias().get(1).getTitularDni());
    }

    @Test
    void faltaUnTitularResponde400() {
        estadia(3L, null);
        estadia(4L, null);

        BookingException ex = assertThrows(BookingException.class, () -> service.cargarTitulares(12L,
                List.of(new TitularRequest(3L, "Ana Pérez", "30111222", "1155555555")), 7L));

        assertEquals("TITULARES_INCOMPLETOS", ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void unTitularSinNombreEsUnErrorDeValidacion() {
        BookingException ex = assertThrows(BookingException.class, () -> service.cargarTitulares(12L,
                List.of(new TitularRequest(3L, " ", "30111222", "1155555555")), 7L));

        assertEquals("VALIDACION", ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void titularesEnUnCarritoVencidoResponde410() {
        estadia(3L, null);
        carrito.setLimiteTiempo(AHORA.minusMinutes(1));

        BookingException ex = assertThrows(BookingException.class, () -> service.cargarTitulares(12L,
                List.of(new TitularRequest(3L, "Ana Pérez", "30111222", "1155555555")), 7L));

        assertEquals("CARRITO_EXPIRADO", ex.getCodigo());
        assertEquals(HttpStatus.GONE, ex.getStatus());
    }

    @Test
    void abandonarLiberaRetencionesYAsientos() {
        conVueloYPasajeros();
        EstadiaHotel e = estadia(3L, "Ana Pérez");

        service.abandonar(12L, 7L);

        // En la transacción: asientos y estado; lo remoto (hotel-service) recién después del commit, sin
        // conexión ni locks abiertos
        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(inventario, bookingRepository);
        orden.verify(inventario).liberarAsientos(carrito);
        orden.verify(bookingRepository).save(carrito);
        orden.verify(inventario).liberarRetencion(e.getRetencionId());
        assertEquals(ReservationStatus.CANCELADA, carrito.getEstado());
        assertEquals("ABANDONADA", carrito.getMotivoCancelacion());
        assertEquals(PaymentStatus.CANCELADO, carrito.getDetalles().get(0).getPaymentStatus());
    }

    @Test
    void abandonarBloqueaLaReservaAntesDeSoltarLasRetenciones() {
        EstadiaHotel e = estadia(3L, "Ana Pérez");

        service.abandonar(12L, 7L);

        // Con la reserva bloqueada, una confirmación de pago en curso no puede quedar CONFIRMADA con
        // las retenciones ya liberadas: o confirma antes (y esto responde 409) o ve la CANCELADA
        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(bookingRepository, inventario);
        orden.verify(bookingRepository).findByIdForUpdate(12L);
        orden.verify(inventario).liberarAsientos(carrito);
        orden.verify(inventario).liberarRetencion(e.getRetencionId());
        verify(bookingRepository, never()).findById(12L);
    }

    @Test
    void unaReservaPagadaNoSeAbandona() {
        carrito.setEstado(ReservationStatus.CONFIRMADA);

        BookingException ex = assertThrows(BookingException.class, () -> service.abandonar(12L, 7L));

        assertEquals("USAR_CANCELACION_POR_ITEM", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(inventario, never()).liberarRetencion(any());
        verify(inventario, never()).liberarAsientos(any());
    }

    @Test
    void unaReservaYaCerradaNoSeAbandonaDeNuevo() {
        carrito.setEstado(ReservationStatus.EXPIRADA);

        assertEquals("RESERVA_CERRADA", assertThrows(BookingException.class, () -> service.abandonar(12L, 7L)).getCodigo());
    }

    @Test
    void noSeAbandonaUnCarritoAjeno() {
        BookingException ex = assertThrows(BookingException.class, () -> service.abandonar(12L, 9L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(inventario, never()).liberarRetencion(any());
        verify(inventario, never()).liberarAsientos(any());
    }

    @Test
    void obtenerSinCarritoActivoDevuelveVacio() {
        conCarritoActivo(null);

        assertTrue(service.obtenerCarrito(7L).isEmpty());
    }

    @Test
    void lasRetencionesDeHotelSeHacenSinTransaccionAbierta() {
        conCarritoActivo(carrito);
        when(hotelClient.crearRetencion(any())).thenAnswer(inv -> {
            // Ni lectura ni escritura del carrito pueden tener una transaccion abierta durante la llamada HTTP
            org.mockito.Mockito.verifyNoInteractions(gestor);
            return retencion(UUID.randomUUID(), "ARS");
        });

        service.agregarEstadia(pedido(), 7L);

        verify(gestor).getTransaction(any());
    }

    @Test
    void siFallaElGuardadoSeLiberaLaRetencion() {
        conCarritoActivo(carrito);
        UUID ret = UUID.randomUUID();
        when(hotelClient.crearRetencion(any())).thenReturn(retencion(ret, "ARS"));
        when(bookingRepository.saveAndFlush(any(Reservation.class))).thenThrow(new IllegalStateException("base caida"));

        assertThrows(IllegalStateException.class, () -> service.agregarEstadia(pedido(), 7L));

        verify(inventario).liberarRetencion(ret);
    }

    @Test
    void siFallaElCommitSeLiberaLaRetencion() {
        conCarritoActivo(carrito);
        UUID ret = UUID.randomUUID();
        when(hotelClient.crearRetencion(any())).thenReturn(retencion(ret, "ARS"));
        org.mockito.Mockito.doThrow(new org.springframework.transaction.TransactionSystemException("commit fallido"))
                .when(gestor).commit(any());

        assertThrows(org.springframework.transaction.TransactionSystemException.class, () -> service.agregarEstadia(pedido(), 7L));

        verify(inventario).liberarRetencion(ret);
    }

    @Test
    void siElCarritoVencioDuranteLaLlamadaAlHotelSeLiberaYResponde410() {
        conCarritoActivo(carrito);
        UUID ret = UUID.randomUUID();
        when(hotelClient.crearRetencion(any())).thenAnswer(inv -> {
            carrito.setLimiteTiempo(AHORA.minusMinutes(1));
            return retencion(ret, "ARS");
        });

        BookingException ex = assertThrows(BookingException.class, () -> service.agregarEstadia(pedido(), 7L));

        assertEquals("CARRITO_EXPIRADO", ex.getCodigo());
        assertEquals(HttpStatus.GONE, ex.getStatus());
        verify(inventario).liberarRetencion(ret);
        assertTrue(carrito.getEstadias().isEmpty());
    }

    @Test
    void siElCarritoSeCerroDuranteLaLlamadaAlHotelSeLiberaYResponde409() {
        conCarritoActivo(carrito);
        UUID ret = UUID.randomUUID();
        when(hotelClient.crearRetencion(any())).thenAnswer(inv -> {
            carrito.setEstado(ReservationStatus.CANCELADA);
            return retencion(ret, "ARS");
        });

        BookingException ex = assertThrows(BookingException.class, () -> service.agregarEstadia(pedido(), 7L));

        assertEquals("ESTADO_INVALIDO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(inventario).liberarRetencion(ret);
    }

    @Test
    void unaRetencionSinPrecioOSinPoliticaSeLiberaYNoQuedaEnElCarrito() {
        conCarritoActivo(carrito);
        UUID sinPrecio = UUID.randomUUID();
        UUID sinPolitica = UUID.randomUUID();
        RetencionHotelResponse a = retencion(sinPrecio, "ARS");
        a.setPrecioTotal(null);
        RetencionHotelResponse b = retencion(sinPolitica, "ARS");
        b.setPoliticaCancelacion(null);
        when(hotelClient.crearRetencion(any())).thenReturn(a, b);

        assertThrows(RuntimeException.class, () -> service.agregarEstadia(pedido(), 7L));
        assertThrows(RuntimeException.class, () -> service.agregarEstadia(pedido(), 7L));

        verify(inventario).liberarRetencion(sinPrecio);
        verify(inventario).liberarRetencion(sinPolitica);
        assertTrue(carrito.getEstadias().isEmpty());
    }

    @Test
    void sinCarritoYSinLugarElCarritoRecienCreadoQuedaCerrado() {
        conCarritoActivo(null);
        AtomicReference<Reservation> creado = new AtomicReference<>();
        when(bookingRepository.saveAndFlush(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(40L);
            creado.set(r);
            return r;
        });
        when(bookingRepository.findById(40L)).thenAnswer(inv -> Optional.of(creado.get()));
        when(hotelClient.crearRetencion(any())).thenThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL",
                "No quedan habitaciones de ese tipo para esas fechas.", HttpStatus.CONFLICT));

        assertThrows(BookingException.class, () -> service.agregarEstadia(pedido(), 7L));

        assertEquals(ReservationStatus.CANCELADA, creado.get().getEstado());
        assertEquals("CARRITO_VACIO", creado.get().getMotivoCancelacion());
    }

    @Test
    void quitarUnaEstadiaLiberaLaRetencionRecienDespuesDelCommit() {
        conVueloYPasajeros();
        EstadiaHotel e = estadia(3L, null);
        conCarritoActivo(carrito);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.quitarEstadia(3L, 7L);

            verify(inventario, never()).liberarRetencion(any());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(inventario).liberarRetencion(e.getRetencionId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void quitarElVueloDeUnCarritoVencidoResponde410() {
        conVueloYPasajeros();
        carrito.setLimiteTiempo(AHORA.minusMinutes(1));
        conCarritoActivo(carrito);

        BookingException ex = assertThrows(BookingException.class, () -> service.quitarVuelo(7L));

        assertEquals("CARRITO_EXPIRADO", ex.getCodigo());
        assertEquals(HttpStatus.GONE, ex.getStatus());
        verify(inventario, never()).liberarAsientos(any());
    }

    @Test
    void quitarUnaEstadiaDeUnCarritoVencidoResponde410() {
        estadia(3L, null);
        carrito.setLimiteTiempo(AHORA.minusMinutes(1));
        conCarritoActivo(carrito);

        assertEquals("CARRITO_EXPIRADO", assertThrows(BookingException.class, () -> service.quitarEstadia(3L, 7L)).getCodigo());
        verify(inventario, never()).liberarRetencion(any());
    }

    @Test
    void titularesDeUnCarritoAjenoResponden403AunqueElCuerpoSeaInvalido() {
        BookingException ex = assertThrows(BookingException.class, () -> service.cargarTitulares(12L,
                List.of(new TitularRequest(3L, " ", "x", "y")), 9L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"+54 11 5555-5555", "(011) 4444 5555", "1155555555", "123456"})
    void telefonosValidos(String telefono) {
        estadia(3L, null);

        ReservationResponse r = service.cargarTitulares(12L, List.of(new TitularRequest(3L, "Ana Pérez", "30111222", telefono)), 7L);

        assertEquals(telefono, carrito.getEstadias().get(0).getTitularTelefono());
        assertTrue(r.getDatosCompletos());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "abc-defg-hij", "11 5555 5555 ext. 9", "+54 11 5555-5555 5555-5555-5555-5555", ""})
    void telefonosInvalidos(String telefono) {
        estadia(3L, null);

        BookingException ex = assertThrows(BookingException.class, () -> service.cargarTitulares(12L,
                List.of(new TitularRequest(3L, "Ana Pérez", "30111222", telefono)), 7L));

        assertEquals("VALIDACION", ex.getCodigo());
    }

    @Test
    void unEstadiaIdRepetidoNoCuentaComoTitularDeLaOtraEstadia() {
        estadia(3L, null);
        estadia(4L, null);

        BookingException ex = assertThrows(BookingException.class, () -> service.cargarTitulares(12L, List.of(
                new TitularRequest(3L, "Ana Pérez", "30111222", "1155555555"),
                new TitularRequest(3L, "Luis Gómez", "28999111", "1144444444")), 7L));

        assertEquals("TITULARES_INCOMPLETOS", ex.getCodigo());
        assertNull(carrito.getEstadias().get(0).getTitularNombre());
    }

    @Test
    void unEstadiaIdQueNoEsDelCarritoNoSeAcepta() {
        estadia(3L, null);

        BookingException ex = assertThrows(BookingException.class, () -> service.cargarTitulares(12L,
                List.of(new TitularRequest(99L, "Ana Pérez", "30111222", "1155555555")), 7L));

        assertEquals("TITULARES_INCOMPLETOS", ex.getCodigo());
    }

    @Test
    void siOtroPedidoCreoElCarritoPrimeroSeReutilizaEse() {
        when(bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(eq(7L), any()))
                .thenReturn(Optional.empty(), Optional.of(carrito));
        when(bookingRepository.saveAndFlush(any(Reservation.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("carrito_abierto_de duplicado"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(hotelClient.crearRetencion(any())).thenReturn(retencion(UUID.randomUUID(), "ARS"));

        ReservationResponse r = service.agregarEstadia(pedido(), 7L);

        assertEquals(12L, r.getIdCarrito());
        assertEquals(1, carrito.getEstadias().size());
    }

    @Test
    void titularesSobreUnaReservaYaConfirmadaResponden409SinTocarLasEstadias() {
        estadia(3L, null);
        Reservation enLaBase = confirmadaEnLaBase();

        BookingException ex = assertThrows(BookingException.class, () -> service.cargarTitulares(12L,
                List.of(new TitularRequest(3L, "Otro Nombre", "40111222", "1155555555")), 7L));

        assertEquals("ESTADO_INVALIDO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Ana Pérez", enLaBase.getEstadias().get(0).getTitularNombre());
        assertEquals(PaymentStatus.PAGADO, enLaBase.getEstadias().get(0).getEstadoPago());
        assertNull(carrito.getEstadias().get(0).getTitularNombre());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void quitarUnaEstadiaReleeLaReservaBloqueada() {
        estadia(3L, null);
        conCarritoActivo(carrito);
        Reservation enLaBase = confirmadaEnLaBase();

        BookingException ex = assertThrows(BookingException.class, () -> service.quitarEstadia(3L, 7L));

        assertEquals("ESTADO_INVALIDO", ex.getCodigo());
        assertEquals(1, enLaBase.getEstadias().size());
        verify(inventario, never()).liberarRetencion(any());
    }

    @Test
    void quitarElVueloReleeLaReservaBloqueada() {
        conVueloYPasajeros();
        conCarritoActivo(carrito);
        confirmadaEnLaBase();

        assertEquals("ESTADO_INVALIDO", assertThrows(BookingException.class, () -> service.quitarVuelo(7L)).getCodigo());

        verify(inventario, never()).liberarAsientos(any());
    }

    @Test
    void agregarUnaEstadiaGuardaSobreLaReservaBloqueada() {
        conCarritoActivo(carrito);
        UUID ret = UUID.randomUUID();
        when(hotelClient.crearRetencion(any())).thenReturn(retencion(ret, "ARS"));
        Reservation enLaBase = confirmadaEnLaBase();

        assertEquals("ESTADO_INVALIDO", assertThrows(BookingException.class,
                () -> service.agregarEstadia(pedido(), 7L)).getCodigo());

        assertEquals(1, enLaBase.getEstadias().size());
        verify(inventario).liberarRetencion(ret);
    }

    @Test
    void abandonarLiberaLasRetencionesRecienDespuesDelCommit() {
        EstadiaHotel e = estadia(3L, "Ana Pérez");
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.abandonar(12L, 7L);
            verify(inventario, never()).liberarRetencion(any());

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

            verify(inventario).liberarRetencion(e.getRetencionId());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
