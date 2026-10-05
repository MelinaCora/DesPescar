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
        when(seatRepository.findByStatusSeatAndBloqueadoHastaBefore("RESERVADO_TEMPORAL", AHORA)).thenReturn(List.of(vencido));

        new SeatReleaseScheduler(seatRepository, messagingTemplate, RELOJ).liberarAsientosExpirados();

        assertEquals("DISPONIBLE", vencido.getStatusSeat());
        assertNull(vencido.getBlockedByUserId());
        verify(messagingTemplate).convertAndSend(eq("/topic/flight/" + VUELO), any(Object.class));
    }
}
