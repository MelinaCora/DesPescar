package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.repository.BookingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;

class CarritoSoporteTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final InventarioCarrito inventario = mock(InventarioCarrito.class);
    private final CarritoSoporte soporte = new CarritoSoporte(bookingRepository, RELOJ, mock(PlatformTransactionManager.class), inventario);

    private Reservation abierto(long id, LocalDateTime limite) {
        return Reservation.builder().id(id).creadorId(7L).cantidadPasajeros(0).tipoPago(PaymentType.SINGLE_PAYMENT)
                .estado(ReservationStatus.INICIADA).limiteTiempo(limite).build();
    }

    @Test
    void alCrearElNuevoSeSueltanLosAsientosDelCarritoVencidoBajoElBloqueoDeLaReserva() {
        Reservation vencido = abierto(5L, AHORA.minusMinutes(1));
        when(bookingRepository.findIdsCarritoAbierto(any(), any())).thenReturn(List.of(5L));
        when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(vencido));
        when(bookingRepository.saveAndFlush(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation nuevo = soporte.crearCarrito(7L);

        assertEquals(ReservationStatus.EXPIRADA, vencido.getEstado());
        assertEquals(ReservationStatus.INICIADA, nuevo.getEstado());
        InOrder orden = inOrder(bookingRepository, inventario);
        orden.verify(bookingRepository).findByIdForUpdate(5L);
        orden.verify(inventario).liberarAsientos(vencido);
    }

    @Test
    void sinCarritoVencidoNoSeTocaElInventario() {
        when(bookingRepository.findIdsCarritoAbierto(any(), any())).thenReturn(List.of());
        when(bookingRepository.saveAndFlush(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        soporte.crearCarrito(7L);

        verify(inventario, never()).liberarAsientos(any());
    }
}
