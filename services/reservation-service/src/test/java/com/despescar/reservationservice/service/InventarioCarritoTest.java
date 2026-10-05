package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.dto.hotel.RetencionHotelRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.SeatRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class InventarioCarritoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant AHORA = Instant.parse("2026-10-05T18:00:00Z"); // 15:00 en Buenos Aires
    private static final LocalDateTime LIMITE = LocalDateTime.of(2026, 10, 5, 15, 15);
    private static final UUID VUELO = UUID.randomUUID();

    @Mock
    private HotelClient hotelClient;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private InventarioCarrito inventario;
    private Reservation reserva;

    @BeforeEach
    void setUp() {
        inventario = new InventarioCarrito(hotelClient, seatRepository, messagingTemplate, Clock.fixed(AHORA, ZONA));
        reserva = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(2)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.PENDIENTE_PAGO)
                .limiteTiempo(LIMITE).flightIds(new ArrayList<>(List.of(VUELO))).build();
    }

    private Seat asiento(String numero, String estado, Long bloqueadoPor) {
        Seat s = new Seat();
        s.setSeatUuid(UUID.randomUUID());
        s.setFlightId(VUELO);
        s.setNumberSeat(numero);
        s.setStatusSeat(estado);
        s.setBlockedByUserId(bloqueadoPor);
        s.setBloqueadoHasta(estado.equals("RESERVADO_TEMPORAL") ? LIMITE : null);
        when(seatRepository.findByFlightIdAndNumberSeatForUpdate(VUELO, numero)).thenReturn(Optional.of(s));
        reserva.getDetalles().add(ReservationDetail.builder().reservation(reserva).outboundSeatNumber(numero).build());
        return s;
    }

    private EstadiaHotel estadia(UUID retencion) {
        EstadiaHotel e = new EstadiaHotel();
        e.setReservation(reserva);
        e.setHotelId(UUID.randomUUID());
        e.setTipoHabitacionId(UUID.randomUUID());
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setCantidadHabitaciones(1);
        e.setHuespedes(2);
        e.setRetencionId(retencion);
        e.setTitularNombre("Ana Pérez");
        reserva.getEstadias().add(e);
        return e;
    }

    @Test
    void elVencimientoEsLaHoraDelCarritoEnUtc() {
        assertEquals(Instant.parse("2026-10-05T18:15:00Z"), inventario.vencimiento(reserva));
    }

    @Test
    void confirmarAsientosOcupaLosDelCreador() {
        Seat a = asiento("1A", "RESERVADO_TEMPORAL", 7L);
        Seat b = asiento("1B", "DISPONIBLE", null);

        assertTrue(inventario.confirmarAsientos(reserva));

        assertEquals("OCUPADO", a.getStatusSeat());
        assertEquals("OCUPADO", b.getStatusSeat());
        assertEquals(7L, b.getBlockedByUserId());
        assertNull(a.getBloqueadoHasta());
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/flight/" + VUELO), any(Object.class));
    }

    @Test
    void siUnAsientoLoTomoOtroNoCambiaNinguno() {
        Seat a = asiento("1A", "RESERVADO_TEMPORAL", 7L);
        asiento("1B", "RESERVADO_TEMPORAL", 9L);

        assertFalse(inventario.confirmarAsientos(reserva));

        assertEquals("RESERVADO_TEMPORAL", a.getStatusSeat());
        verify(seatRepository, never()).save(any());
    }

    @Test
    void liberarAsientosSoloTocaLosDelCreador() {
        Seat mio = asiento("1A", "OCUPADO", 7L);
        mio.setReservaId(12L);
        Seat ajeno = asiento("1B", "RESERVADO_TEMPORAL", 9L);

        inventario.liberarAsientos(reserva);

        assertEquals("DISPONIBLE", mio.getStatusSeat());
        assertNull(mio.getBlockedByUserId());
        assertEquals("RESERVADO_TEMPORAL", ajeno.getStatusSeat());
    }

    @Test
    void alinearBloqueosExtiendeLosAsientosDelCreadorHastaElLimite() {
        Seat mio = new Seat();
        mio.setFlightId(VUELO);
        mio.setNumberSeat("1A");
        mio.setStatusSeat("RESERVADO_TEMPORAL");
        mio.setBlockedByUserId(7L);
        mio.setBloqueadoHasta(LIMITE.minusMinutes(5));
        Seat ajeno = new Seat();
        ajeno.setFlightId(VUELO);
        ajeno.setNumberSeat("1B");
        ajeno.setStatusSeat("RESERVADO_TEMPORAL");
        ajeno.setBlockedByUserId(9L);
        ajeno.setBloqueadoHasta(LIMITE.minusMinutes(5));
        when(seatRepository.findByFlightId(VUELO)).thenReturn(List.of(mio, ajeno));

        inventario.alinearBloqueos(reserva);

        assertEquals(LIMITE, mio.getBloqueadoHasta());
        assertEquals(LIMITE.minusMinutes(5), ajeno.getBloqueadoHasta());
    }

    @Test
    void confirmarEstadiasUsaElTitular() {
        UUID ret = UUID.randomUUID();
        estadia(ret);

        assertTrue(inventario.confirmarEstadias(reserva));

        verify(hotelClient).confirmarRetencion(ret, "Ana Pérez");
    }

    @Test
    void unaRetencionLiberadaSeTomaDeNuevoYSeConfirma() {
        UUID vieja = UUID.randomUUID();
        UUID nueva = UUID.randomUUID();
        EstadiaHotel e = estadia(vieja);
        when(hotelClient.confirmarRetencion(vieja, "Ana Pérez"))
                .thenThrow(new BookingException("RETENCION_LIBERADA", "La retención ya fue liberada.", HttpStatus.CONFLICT));
        RetencionHotelResponse creada = new RetencionHotelResponse();
        creada.setRetencionId(nueva);
        when(hotelClient.crearRetencion(any())).thenReturn(creada);

        assertTrue(inventario.confirmarEstadias(reserva));

        assertEquals(nueva, e.getRetencionId());
        verify(hotelClient).confirmarRetencion(nueva, "Ana Pérez");
        ArgumentCaptor<RetencionHotelRequest> pedido = ArgumentCaptor.forClass(RetencionHotelRequest.class);
        verify(hotelClient).crearRetencion(pedido.capture());
        assertEquals(12L, pedido.getValue().reservaId());
        assertEquals(AHORA.plusSeconds(600), pedido.getValue().expiraEn());
    }

    @Test
    void retenerYConfirmarNoUsaLaRetencionViejaDeUnaExpirada() {
        UUID vieja = UUID.randomUUID();
        UUID nueva = UUID.randomUUID();
        EstadiaHotel e = estadia(vieja);
        RetencionHotelResponse creada = new RetencionHotelResponse();
        creada.setRetencionId(nueva);
        when(hotelClient.crearRetencion(any())).thenReturn(creada);

        assertTrue(inventario.retenerYConfirmarEstadias(reserva));

        assertEquals(nueva, e.getRetencionId());
        verify(hotelClient).confirmarRetencion(nueva, "Ana Pérez");
        verify(hotelClient, never()).confirmarRetencion(vieja, "Ana Pérez");
        verify(hotelClient, never()).liberarRetencion(any());
    }

    @Test
    void retenerYConfirmarSinLugarSueltaLasNuevasTomadas() {
        UUID primeraNueva = UUID.randomUUID();
        estadia(UUID.randomUUID());
        estadia(UUID.randomUUID());
        RetencionHotelResponse creada = new RetencionHotelResponse();
        creada.setRetencionId(primeraNueva);
        when(hotelClient.crearRetencion(any())).thenReturn(creada)
                .thenThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL", "Sin lugar", HttpStatus.CONFLICT));

        assertFalse(inventario.retenerYConfirmarEstadias(reserva));

        verify(hotelClient).liberarRetencion(primeraNueva);
    }

    @Test
    void sinLugarNoSueltaLasRetencionesPreviasDelCarrito() {
        UUID primera = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        estadia(primera);
        estadia(segunda);
        when(hotelClient.confirmarRetencion(primera, "Ana Pérez")).thenReturn(new RetencionHotelResponse());
        when(hotelClient.confirmarRetencion(segunda, "Ana Pérez"))
                .thenThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL", "Sin lugar", HttpStatus.CONFLICT));

        assertFalse(inventario.confirmarEstadias(reserva));

        // La primera es del carrito (quizás ya la usa otro intento que confirmó): no se suelta acá.
        // Si la reserva se cancela, BookingService la libera después del commit.
        verify(hotelClient, never()).liberarRetencion(any());
    }

    @Test
    void sinLugarSoloSueltaLaRetencionRescatadaEnEsteIntento() {
        UUID vieja = UUID.randomUUID();
        UUID nueva = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        estadia(vieja);
        estadia(segunda);
        when(hotelClient.confirmarRetencion(vieja, "Ana Pérez"))
                .thenThrow(new BookingException("RETENCION_LIBERADA", "liberada", HttpStatus.CONFLICT));
        RetencionHotelResponse creada = new RetencionHotelResponse();
        creada.setRetencionId(nueva);
        when(hotelClient.crearRetencion(any())).thenReturn(creada);
        when(hotelClient.confirmarRetencion(nueva, "Ana Pérez")).thenReturn(new RetencionHotelResponse());
        when(hotelClient.confirmarRetencion(segunda, "Ana Pérez"))
                .thenThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL", "Sin lugar", HttpStatus.CONFLICT));

        assertFalse(inventario.confirmarEstadias(reserva));

        verify(hotelClient).liberarRetencion(nueva);
        verify(hotelClient, never()).liberarRetencion(vieja);
        verify(hotelClient, never()).liberarRetencion(segunda);
    }

    @Test
    void unErrorDeComunicacionSePropaga() {
        UUID ret = UUID.randomUUID();
        estadia(ret);
        when(hotelClient.confirmarRetencion(ret, "Ana Pérez"))
                .thenThrow(new BookingException("HOTEL_SERVICE_UNAVAILABLE", "caido", HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(BookingException.class, () -> inventario.confirmarEstadias(reserva));
    }

    @Test
    void liberarRetencionesNoCortaPorUnError() {
        UUID primera = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        estadia(primera);
        estadia(segunda);
        doThrow(new BookingException("HOTEL_SERVICE_UNAVAILABLE", "caido", HttpStatus.SERVICE_UNAVAILABLE))
                .when(hotelClient).liberarRetencion(primera);

        inventario.liberarRetenciones(reserva);

        verify(hotelClient).liberarRetencion(segunda);
    }

    @Test
    void sinVueloNoHayAsientosQueTocar() {
        reserva.setFlightIds(new ArrayList<>());
        assertTrue(inventario.confirmarAsientos(reserva));
        inventario.liberarAsientos(reserva);
        inventario.alinearBloqueos(reserva);
        verify(seatRepository, never()).findByFlightIdAndNumberSeatForUpdate(any(), anyString());
    }

    @Test
    void siElRescateFallaPorFaltaDeLugarDevuelveFalse() {
        UUID vieja = UUID.randomUUID();
        estadia(vieja);
        when(hotelClient.confirmarRetencion(vieja, "Ana Pérez"))
                .thenThrow(new BookingException("RETENCION_LIBERADA", "liberada", HttpStatus.CONFLICT));
        when(hotelClient.crearRetencion(any()))
                .thenThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL", "Sin lugar", HttpStatus.CONFLICT));

        assertFalse(inventario.confirmarEstadias(reserva));
    }

    @Test
    void siFallaConfirmarLaRetencionNuevaSeLibera() {
        UUID vieja = UUID.randomUUID();
        UUID nueva = UUID.randomUUID();
        estadia(vieja);
        when(hotelClient.confirmarRetencion(vieja, "Ana Pérez"))
                .thenThrow(new BookingException("RETENCION_LIBERADA", "liberada", HttpStatus.CONFLICT));
        RetencionHotelResponse creada = new RetencionHotelResponse();
        creada.setRetencionId(nueva);
        when(hotelClient.crearRetencion(any())).thenReturn(creada);
        when(hotelClient.confirmarRetencion(nueva, "Ana Pérez"))
                .thenThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL", "Sin lugar", HttpStatus.CONFLICT));

        assertFalse(inventario.confirmarEstadias(reserva));

        verify(hotelClient).liberarRetencion(nueva);
    }

    @Test
    void siFallaConfirmarLaRetencionNuevaPorComunicacionSeLiberaYPropaga() {
        UUID vieja = UUID.randomUUID();
        UUID nueva = UUID.randomUUID();
        estadia(vieja);
        when(hotelClient.confirmarRetencion(vieja, "Ana Pérez"))
                .thenThrow(new BookingException("RETENCION_LIBERADA", "liberada", HttpStatus.CONFLICT));
        RetencionHotelResponse creada = new RetencionHotelResponse();
        creada.setRetencionId(nueva);
        when(hotelClient.crearRetencion(any())).thenReturn(creada);
        when(hotelClient.confirmarRetencion(nueva, "Ana Pérez"))
                .thenThrow(new BookingException("HOTEL_SERVICE_TIMEOUT", "timeout", HttpStatus.GATEWAY_TIMEOUT));

        assertThrows(BookingException.class, () -> inventario.confirmarEstadias(reserva));

        verify(hotelClient).liberarRetencion(nueva);
    }

    @Test
    void unErrorDeComunicacionTrasUnaEstadiaConfirmadaLaLiberaYPropaga() {
        UUID primera = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        estadia(primera);
        estadia(segunda);
        when(hotelClient.confirmarRetencion(primera, "Ana Pérez")).thenReturn(new RetencionHotelResponse());
        when(hotelClient.confirmarRetencion(segunda, "Ana Pérez"))
                .thenThrow(new BookingException("HOTEL_SERVICE_UNAVAILABLE", "caido", HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(BookingException.class, () -> inventario.confirmarEstadias(reserva));

        // Un 503 en la segunda no suelta la primera: es del carrito y otro intento pudo confirmarla
        verify(hotelClient, never()).liberarRetencion(any());
    }

    @Test
    void liberarRetencionAtrapaCualquierErrorInesperado() {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("boom")).when(hotelClient).liberarRetencion(id);

        inventario.liberarRetencion(id);

        verify(hotelClient).liberarRetencion(id);
    }

    @Test
    void alinearBloqueosGuardaElAsientoModificado() {
        Seat mio = new Seat();
        mio.setFlightId(VUELO);
        mio.setNumberSeat("1A");
        mio.setStatusSeat("RESERVADO_TEMPORAL");
        mio.setBlockedByUserId(7L);
        mio.setBloqueadoHasta(LIMITE.minusMinutes(5));
        when(seatRepository.findByFlightId(VUELO)).thenReturn(List.of(mio));

        inventario.alinearBloqueos(reserva);

        verify(seatRepository).save(mio);
    }

    @Test
    void confirmarUnAsientoYaOcupadoPorElCreadorEsIdempotente() {
        Seat a = asiento("1A", "OCUPADO", 7L);
        a.setReservaId(12L);

        assertTrue(inventario.confirmarAsientos(reserva));
        assertTrue(inventario.confirmarAsientos(reserva));

        assertEquals("OCUPADO", a.getStatusSeat());
        assertEquals(7L, a.getBlockedByUserId());
    }

    @Test
    void liberarAsientosNoGuardaLosQueYaEstanDisponibles() {
        asiento("1A", "DISPONIBLE", 7L);

        inventario.liberarAsientos(reserva);

        verify(seatRepository, never()).save(any());
    }

    @Test
    void losAsientosSeBloqueanEnOrdenPorNumero() {
        asiento("2A", "DISPONIBLE", null);
        asiento("1B", "DISPONIBLE", null);
        asiento("1A", "DISPONIBLE", null);

        inventario.confirmarAsientos(reserva);
        inventario.liberarAsientos(reserva);

        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(seatRepository);
        for (int vez = 0; vez < 2; vez++) {
            orden.verify(seatRepository).findByFlightIdAndNumberSeatForUpdate(VUELO, "1A");
            orden.verify(seatRepository).findByFlightIdAndNumberSeatForUpdate(VUELO, "1B");
            orden.verify(seatRepository).findByFlightIdAndNumberSeatForUpdate(VUELO, "2A");
        }
    }

    @Test
    void sinTransaccionElAvisoSaleAlInstante() {
        asiento("1A", "DISPONIBLE", null);

        inventario.confirmarAsientos(reserva);

        verify(messagingTemplate).convertAndSend(eq("/topic/flight/" + VUELO), any(Object.class));
    }

    @Test
    void conTransaccionElAvisoEsperaAlCommit() {
        asiento("1A", "DISPONIBLE", null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            inventario.confirmarAsientos(reserva);
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(messagingTemplate).convertAndSend(eq("/topic/flight/" + VUELO), any(Object.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void conCreadorNuloNoHayExcepcion() {
        reserva.setCreadorId(null);
        asiento("1A", "DISPONIBLE", null);
        inventario.liberarAsientos(reserva);
        assertTrue(inventario.confirmarAsientos(reserva));
    }

    @Test
    void noOcupaUnAsientoQueElCreadorTieneParaOtroCarrito() {
        Seat a = asiento("1A", "RESERVADO_TEMPORAL", 7L);
        a.setReservaId(99L);

        assertFalse(inventario.confirmarAsientos(reserva));

        assertEquals("RESERVADO_TEMPORAL", a.getStatusSeat());
        assertEquals(99L, a.getReservaId());
    }

    @Test
    void noOcupaUnAsientoQueOtraReservaYaPago() {
        Seat a = asiento("1A", "OCUPADO", 7L);
        a.setReservaId(99L);

        assertFalse(inventario.confirmarAsientos(reserva));
    }

    @Test
    void confirmarDejaElAsientoATadoALaReserva() {
        Seat a = asiento("1A", "RESERVADO_TEMPORAL", 7L);
        a.setReservaId(12L);
        Seat b = asiento("1B", "DISPONIBLE", null);

        assertTrue(inventario.confirmarAsientos(reserva));

        assertEquals(12L, a.getReservaId());
        assertEquals(12L, b.getReservaId());
    }

    @Test
    void unaReservaExpiradaSoloTomaAsientosLibresOSuyos() {
        reserva.setEstado(ReservationStatus.EXPIRADA);
        // Bloqueado por el usuario desde el mapa, sin carrito: puede ser para otra compra
        Seat a = asiento("1A", "RESERVADO_TEMPORAL", 7L);

        assertFalse(inventario.confirmarAsientos(reserva));
        assertEquals("RESERVADO_TEMPORAL", a.getStatusSeat());
    }

    @Test
    void unaReservaExpiradaTomaAsientosDisponibles() {
        reserva.setEstado(ReservationStatus.EXPIRADA);
        Seat a = asiento("1A", "DISPONIBLE", null);

        assertTrue(inventario.confirmarAsientos(reserva));
        assertEquals("OCUPADO", a.getStatusSeat());
    }

    @Test
    void liberarNuncaSueltaUnAsientoPagadoPorOtraReserva() {
        Seat pagado = asiento("1A", "OCUPADO", 7L);
        pagado.setReservaId(99L);
        Seat legado = asiento("1B", "OCUPADO", 7L); // pagado antes de reserva_id: no se sabe de quién es

        inventario.liberarAsientos(reserva);

        assertEquals("OCUPADO", pagado.getStatusSeat());
        assertEquals("OCUPADO", legado.getStatusSeat());
        verify(seatRepository, never()).save(any());
    }

    @Test
    void liberarNoSueltaElBloqueoDeOtroCarritoDelMismoUsuario() {
        Seat otro = asiento("1A", "RESERVADO_TEMPORAL", 7L);
        otro.setReservaId(99L);

        inventario.liberarAsientos(reserva);

        assertEquals("RESERVADO_TEMPORAL", otro.getStatusSeat());
    }

    @Test
    void liberarSueltaYDesataLosDeEstaReserva() {
        Seat mio = asiento("1A", "RESERVADO_TEMPORAL", 7L);
        mio.setReservaId(12L);

        inventario.liberarAsientos(reserva);

        assertEquals("DISPONIBLE", mio.getStatusSeat());
        assertNull(mio.getReservaId());
    }

    @Test
    void alinearBloqueosAtaLosAsientosAlCarrito() {
        Seat mio = new Seat();
        mio.setFlightId(VUELO);
        mio.setNumberSeat("1A");
        mio.setStatusSeat("RESERVADO_TEMPORAL");
        mio.setBlockedByUserId(7L);
        when(seatRepository.findByFlightId(VUELO)).thenReturn(List.of(mio));

        inventario.alinearBloqueos(reserva);

        assertEquals(12L, mio.getReservaId());
    }

    @Test
    void liberarDesataLosAsientosElegidosQueNoLlegaronAUnPasajero() {
        // Elegido en el mapa y atado al carrito al entrar el vuelo, pero sin pasajero cargado: al
        // soltar el vuelo sigue bloqueado por el usuario, ya sin carrito, y otro carrito suyo lo puede usar
        Seat elegido = new Seat();
        elegido.setSeatUuid(UUID.randomUUID());
        elegido.setFlightId(VUELO);
        elegido.setNumberSeat("9C");
        elegido.setStatusSeat("RESERVADO_TEMPORAL");
        elegido.setBlockedByUserId(7L);
        elegido.setBloqueadoHasta(LIMITE);
        elegido.setReservaId(12L);
        when(seatRepository.findIdsByFlightIdAndReservaId(VUELO, 12L)).thenReturn(List.of(elegido.getSeatUuid()));
        when(seatRepository.findByIdForUpdate(elegido.getSeatUuid())).thenReturn(Optional.of(elegido));

        inventario.liberarAsientos(reserva);

        assertNull(elegido.getReservaId());
        assertEquals("RESERVADO_TEMPORAL", elegido.getStatusSeat());
        assertEquals(7L, elegido.getBlockedByUserId());
        verify(seatRepository).save(elegido);
    }

    @Test
    void liberarNoDesataUnAsientoQueYaSePago() {
        Seat pagado = new Seat();
        pagado.setSeatUuid(UUID.randomUUID());
        pagado.setFlightId(VUELO);
        pagado.setNumberSeat("9D");
        pagado.setStatusSeat("OCUPADO");
        pagado.setBlockedByUserId(7L);
        pagado.setReservaId(12L);
        when(seatRepository.findIdsByFlightIdAndReservaId(VUELO, 12L)).thenReturn(List.of(pagado.getSeatUuid()));
        when(seatRepository.findByIdForUpdate(pagado.getSeatUuid())).thenReturn(Optional.of(pagado));

        inventario.liberarAsientos(reserva);

        assertEquals(12L, pagado.getReservaId());
        verify(seatRepository, never()).save(any());
    }
}
