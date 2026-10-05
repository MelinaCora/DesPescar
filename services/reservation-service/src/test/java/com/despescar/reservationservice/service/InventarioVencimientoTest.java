package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoItem;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.SeatRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class InventarioVencimientoTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T18:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final Instant NUEVO = AHORA.plusSeconds(24 * 3600);
    private static final Instant ANTERIOR = AHORA.plusSeconds(600);

    @Mock
    private HotelClient hotelClient;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private InventarioCarrito inventario;
    private Reservation reserva;
    private UUID primera;
    private UUID segunda;

    @BeforeEach
    void setUp() {
        inventario = new InventarioCarrito(hotelClient, seatRepository, messagingTemplate, RELOJ);
        reserva = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0).build();
        primera = estadia(EstadoItem.ACTIVA);
        segunda = estadia(EstadoItem.ACTIVA);
        estadia(EstadoItem.CANCELADA);
    }

    private UUID estadia(EstadoItem estado) {
        EstadiaHotel e = new EstadiaHotel();
        e.setRetencionId(UUID.randomUUID());
        e.setEstado(estado);
        e.setReservation(reserva);
        reserva.getEstadias().add(e);
        return e.getRetencionId();
    }

    @Test
    void cambiaElVencimientoDeLasEstadiasActivas() {
        inventario.cambiarVencimientoRetenciones(reserva, NUEVO, ANTERIOR);

        verify(hotelClient).cambiarVencimiento(primera, NUEVO);
        verify(hotelClient).cambiarVencimiento(segunda, NUEVO);
        verify(hotelClient, never()).liberarRetencion(any());
    }

    @Test
    void siUnaFallaLasYaCambiadasVuelvenAlVencimientoAnteriorYSePropaga() {
        lenient().when(hotelClient.cambiarVencimiento(segunda, NUEVO)).thenThrow(
                new BookingException("SIN_DISPONIBILIDAD_HOTEL", "sin lugar", HttpStatus.CONFLICT));

        BookingException ex = assertThrows(BookingException.class,
                () -> inventario.cambiarVencimientoRetenciones(reserva, NUEVO, ANTERIOR));

        assertEquals("SIN_DISPONIBILIDAD_HOTEL", ex.getCodigo());
        InOrder orden = inOrder(hotelClient);
        orden.verify(hotelClient).cambiarVencimiento(primera, NUEVO);
        orden.verify(hotelClient).cambiarVencimiento(segunda, NUEVO);
        orden.verify(hotelClient).cambiarVencimiento(primera, ANTERIOR);
        verify(hotelClient, never()).cambiarVencimiento(segunda, ANTERIOR);
    }

    @Test
    void siElVencimientoAnteriorYaPasoLaCompensacionLibera() {
        Instant pasado = AHORA.minusSeconds(1);

        inventario.volverAlVencimiento(reserva, pasado);

        verify(hotelClient).liberarRetencion(primera);
        verify(hotelClient).liberarRetencion(segunda);
        verify(hotelClient, never()).cambiarVencimiento(any(), any());
    }

    @Test
    void laCompensacionEsMejorEsfuerzo() {
        doThrow(new BookingException("HOTEL_SERVICE_UNAVAILABLE", "caido", HttpStatus.SERVICE_UNAVAILABLE))
                .when(hotelClient).cambiarVencimiento(primera, ANTERIOR);

        inventario.volverAlVencimiento(reserva, ANTERIOR);

        verify(hotelClient).cambiarVencimiento(segunda, ANTERIOR);
    }
}
