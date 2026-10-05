package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.mapper.ReservationDetailMapper;
import com.despescar.reservationservice.mapper.ReservationMapper;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class BookingSchedulerTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    // 18:00 UTC = 15:00 en Buenos Aires: la consulta tiene que usar 15:00, no la hora de la JVM
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BookingScheduler scheduler;
    private Reservation vencida;

    @BeforeEach
    void setUp() {
        scheduler = new BookingScheduler(bookingRepository, messagingTemplate,
                new ReservationMapper(new ReservationDetailMapper(), RELOJ), inventario, RELOJ,
                new TransactionTemplate(transactionManager));
        vencida = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(1)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.PENDIENTE_PAGO)
                .limiteTiempo(AHORA.minusMinutes(1)).flightIds(new ArrayList<>(List.of(UUID.randomUUID())))
                .precioVueloPorPasajero(new BigDecimal("240000.00")).build();
        vencida.getDetalles().add(ReservationDetail.builder().reservation(vencida).outboundSeatNumber("1A")
                .priceCharged(new BigDecimal("240000.00")).paymentStatus(PaymentStatus.PENDIENTE).build());
        EstadiaHotel e = new EstadiaHotel();
        e.setId(3L);
        e.setReservation(vencida);
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(new BigDecimal("580000.00"));
        vencida.getEstadias().add(e);
        when(bookingRepository.findByEstadoInAndLimiteTiempoBefore(any(), eq(AHORA))).thenReturn(List.of(vencida));
    }

    @Test
    void expiraLosVencidosPorLaEntidadYLiberaLasRetencionesDespuesDelCommit() {
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(vencida));
        when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        scheduler.verificarCarritosExpirados();

        assertEquals(ReservationStatus.EXPIRADA, vencida.getEstado());
        // Los pasajeros siguen PENDIENTE: el total no cambia y un pago tardío todavía coincide (D6, D7)
        assertEquals(PaymentStatus.PENDIENTE, vencida.getDetalles().get(0).getPaymentStatus());
        // Se bloquea la reserva para no pisar una confirmación de pago en curso; el estado cambia por la
        // entidad (save), así @PreUpdate deja carrito_abierto_de en null
        InOrder orden = inOrder(bookingRepository, inventario, transactionManager, messagingTemplate);
        orden.verify(bookingRepository).findByIdForUpdate(12L);
        orden.verify(inventario).liberarAsientos(vencida);
        orden.verify(bookingRepository).save(vencida);
        orden.verify(transactionManager).commit(any());
        orden.verify(messagingTemplate).convertAndSend(eq("/topic/reserva/12"), any(Object.class));
        orden.verify(inventario).liberarRetenciones(vencida);
    }

    @Test
    void siSeConfirmoEntreLaConsultaYElCierreNoLaToca() {
        vencida.setEstado(ReservationStatus.CONFIRMADA);
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(vencida));

        scheduler.verificarCarritosExpirados();

        assertEquals(ReservationStatus.CONFIRMADA, vencida.getEstado());
        verifyNoInteractions(inventario, messagingTemplate);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void unAvisoQueFallaNoImpideLiberarLasRetenciones() {
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(vencida));
        when(bookingRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("broker caído")).when(messagingTemplate).convertAndSend(eq("/topic/reserva/12"), any(Object.class));

        scheduler.verificarCarritosExpirados();

        assertEquals(ReservationStatus.EXPIRADA, vencida.getEstado());
        verify(inventario).liberarRetenciones(vencida);
    }
}
