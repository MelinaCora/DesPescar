package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.reservation.response.SeatResponse;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.repository.SeatRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suelta los bloqueos temporales vencidos. "Ahora" sale del Clock de Argentina, la misma zona en la
 * que se guardan bloqueadoHasta y limiteTiempo (D23); con la zona de la JVM en UTC se liberaban tres
 * horas antes. Los asientos OCUPADO (pagados) nunca entran: la consulta pide RESERVADO_TEMPORAL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeatReleaseScheduler {

    private final SeatRepository seatRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void liberarAsientosExpirados() {
        List<Seat> vencidos = seatRepository.findByStatusSeatAndBloqueadoHastaBefore(
                InventarioCarrito.RESERVADO_TEMPORAL, LocalDateTime.now(clock));
        if (vencidos.isEmpty()) {
            return;
        }
        log.info("Liberando {} asientos con el bloqueo vencido", vencidos.size());
        for (Seat seat : vencidos) {
            seat.setStatusSeat(InventarioCarrito.DISPONIBLE);
            seat.setBlockedByUserId(null);
            seat.setBloqueadoHasta(null);
            seatRepository.save(seat);
            messagingTemplate.convertAndSend("/topic/flight/" + seat.getFlightId(), SeatResponse.builder()
                    .seatNumber(seat.getNumberSeat())
                    .seatUuid(seat.getSeatUuid())
                    .seatStatus(InventarioCarrito.DISPONIBLE)
                    .build());
        }
    }
}
