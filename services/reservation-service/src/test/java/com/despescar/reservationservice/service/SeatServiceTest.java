package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.repository.SeatRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** Bloqueos de asientos con la hora de Argentina y sin liberar asientos pagados. */
@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    // 18:00 UTC = 15:00 en Buenos Aires
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final UUID VUELO = UUID.randomUUID();

    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SeatService service;

    @BeforeEach
    void setUp() {
        service = new SeatService(seatRepository, RELOJ);
        lenient().when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Seat asiento(String estado, Long bloqueadoPor) {
        Seat s = new Seat();
        s.setSeatUuid(UUID.randomUUID());
        s.setFlightId(VUELO);
        s.setNumberSeat("1A");
        s.setStatusSeat(estado);
        s.setBlockedByUserId(bloqueadoPor);
        lenient().when(seatRepository.findByIdForUpdate(s.getSeatUuid())).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void bloquearVenceEnQuinceMinutosHoraArgentina() {
        Seat s = asiento("DISPONIBLE", null);

        service.blockedSeat(s.getSeatUuid(), 7L);

        assertEquals("RESERVADO_TEMPORAL", s.getStatusSeat());
        assertEquals(7L, s.getBlockedByUserId());
        assertEquals(AHORA.plusMinutes(15), s.getBloqueadoHasta());
    }

    @Test
    void liberarUnAsientoPropioLoDejaDisponible() {
        Seat s = asiento("RESERVADO_TEMPORAL", 7L);

        service.unblockSeat(s.getSeatUuid(), 7L);

        assertEquals("DISPONIBLE", s.getStatusSeat());
        assertNull(s.getBlockedByUserId());
    }

    @Test
    void unAsientoPagadoNoSeLiberaDesdeElMapa() {
        Seat s = asiento("OCUPADO", 7L);

        assertThrows(RuntimeException.class, () -> service.unblockSeat(s.getSeatUuid(), 7L));

        assertEquals("OCUPADO", s.getStatusSeat());
        assertEquals(7L, s.getBlockedByUserId());
    }

    @Test
    void elSchedulerDeAsientosUsaLaHoraDeArgentina() {
        Seat vencido = asiento("RESERVADO_TEMPORAL", 7L);
        vencido.setBloqueadoHasta(AHORA.minusMinutes(1));
        vencido.setReservaId(12L);
        when(seatRepository.findIdsByStatusSeatAndBloqueadoHastaBefore("RESERVADO_TEMPORAL", AHORA))
                .thenReturn(List.of(vencido.getSeatUuid()));

        new SeatReleaseScheduler(seatRepository, messagingTemplate, RELOJ).liberarAsientosExpirados();

        assertEquals("DISPONIBLE", vencido.getStatusSeat());
        assertNull(vencido.getBlockedByUserId());
        assertNull(vencido.getReservaId());
        verify(messagingTemplate).convertAndSend(eq("/topic/flight/" + VUELO), any(Object.class));
    }

    @Test
    void elSchedulerDeAsientosNoSueltaUnoQueSePagoDespuesDeLaConsulta() {
        Seat pagado = asiento("OCUPADO", 7L);
        pagado.setReservaId(12L);
        when(seatRepository.findIdsByStatusSeatAndBloqueadoHastaBefore("RESERVADO_TEMPORAL", AHORA))
                .thenReturn(List.of(pagado.getSeatUuid()));

        new SeatReleaseScheduler(seatRepository, messagingTemplate, RELOJ).liberarAsientosExpirados();

        assertEquals("OCUPADO", pagado.getStatusSeat());
        assertEquals(7L, pagado.getBlockedByUserId());
        org.mockito.Mockito.verify(seatRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(messagingTemplate);
    }

    @Test
    void elSchedulerDeAsientosNoSueltaUnBloqueoRenovado() {
        Seat renovado = asiento("RESERVADO_TEMPORAL", 7L);
        renovado.setBloqueadoHasta(AHORA.plusMinutes(10)); // el carrito lo extendió después de la consulta
        when(seatRepository.findIdsByStatusSeatAndBloqueadoHastaBefore("RESERVADO_TEMPORAL", AHORA))
                .thenReturn(List.of(renovado.getSeatUuid()));

        new SeatReleaseScheduler(seatRepository, messagingTemplate, RELOJ).liberarAsientosExpirados();

        assertEquals("RESERVADO_TEMPORAL", renovado.getStatusSeat());
    }

    @Test
    void elSchedulerDeAsientosAvisaRecienDespuesDelCommit() {
        Seat vencido = asiento("RESERVADO_TEMPORAL", 7L);
        vencido.setBloqueadoHasta(AHORA.minusMinutes(1));
        when(seatRepository.findIdsByStatusSeatAndBloqueadoHastaBefore("RESERVADO_TEMPORAL", AHORA))
                .thenReturn(List.of(vencido.getSeatUuid()));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            new SeatReleaseScheduler(seatRepository, messagingTemplate, RELOJ).liberarAsientosExpirados();
            org.mockito.Mockito.verifyNoInteractions(messagingTemplate);

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

            verify(messagingTemplate).convertAndSend(eq("/topic/flight/" + VUELO), any(Object.class));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void bloquearUnAsientoTomadoResponde409ConCodigo() {
        Seat s = asiento("RESERVADO_TEMPORAL", 9L);

        com.despescar.reservationservice.exception.BookingException ex = assertThrows(
                com.despescar.reservationservice.exception.BookingException.class, () -> service.blockedSeat(s.getSeatUuid(), 7L));

        assertEquals("ASIENTO_NO_DISPONIBLE", ex.getCodigo());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void unAsientoInexistenteResponde404ConCodigo() {
        UUID id = UUID.randomUUID();
        when(seatRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        com.despescar.reservationservice.exception.BookingException ex = assertThrows(
                com.despescar.reservationservice.exception.BookingException.class, () -> service.blockedSeat(id, 7L));

        assertEquals("ASIENTO_NO_ENCONTRADO", ex.getCodigo());
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void liberarUnAsientoPagadoResponde409AsientoOcupado() {
        Seat s = asiento("OCUPADO", 7L);

        com.despescar.reservationservice.exception.BookingException ex = assertThrows(
                com.despescar.reservationservice.exception.BookingException.class, () -> service.unblockSeat(s.getSeatUuid(), 7L));

        assertEquals("ASIENTO_OCUPADO", ex.getCodigo());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void liberarUnAsientoAjenoResponde409() {
        Seat s = asiento("RESERVADO_TEMPORAL", 9L);

        com.despescar.reservationservice.exception.BookingException ex = assertThrows(
                com.despescar.reservationservice.exception.BookingException.class, () -> service.unblockSeat(s.getSeatUuid(), 7L));

        assertEquals("ASIENTO_NO_DISPONIBLE", ex.getCodigo());
    }

    @Test
    void liberarDesdeElMapaDesataElAsientoDelCarrito() {
        Seat s = asiento("RESERVADO_TEMPORAL", 7L);
        s.setReservaId(12L);

        service.unblockSeat(s.getSeatUuid(), 7L);

        assertNull(s.getReservaId());
    }
}
