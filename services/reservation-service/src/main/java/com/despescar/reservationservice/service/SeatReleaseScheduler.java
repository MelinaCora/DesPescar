package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.reservation.response.SeatResponse;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.repository.SeatRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Suelta los bloqueos temporales vencidos. "Ahora" sale del Clock de Argentina, la misma zona en la
 * que se guardan bloqueadoHasta y limiteTiempo (D23). La consulta trae solo ids y cada asiento se
 * relee bloqueado (FOR UPDATE): uno que se pagó (OCUPADO) o cuyo bloqueo se extendió después de la
 * consulta no se toca. Los avisos por WebSocket salen recién después del commit.
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
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<UUID> candidatos = seatRepository.findIdsByStatusSeatAndBloqueadoHastaBefore(
                InventarioCarrito.RESERVADO_TEMPORAL, ahora);
        List<Aviso> avisos = new ArrayList<>();
        for (UUID id : candidatos) {
            seatRepository.findByIdForUpdate(id)
                    .filter(s -> InventarioCarrito.RESERVADO_TEMPORAL.equals(s.getStatusSeat()))
                    .filter(s -> s.getBloqueadoHasta() != null && s.getBloqueadoHasta().isBefore(ahora))
                    .ifPresent(seat -> {
                        seat.setStatusSeat(InventarioCarrito.DISPONIBLE);
                        seat.setBlockedByUserId(null);
                        seat.setBloqueadoHasta(null);
                        seat.setReservaId(null);
                        seatRepository.save(seat);
                        avisos.add(aviso(seat));
                    });
        }
        if (avisos.isEmpty()) {
            return;
        }
        log.info("Liberando {} asientos con el bloqueo vencido", avisos.size());
        despuesDelCommit(() -> avisos.forEach(this::enviar));
    }

    private static Aviso aviso(Seat seat) {
        return new Aviso("/topic/flight/" + seat.getFlightId(), SeatResponse.builder()
                .seatNumber(seat.getNumberSeat())
                .seatUuid(seat.getSeatUuid())
                .seatStatus(InventarioCarrito.DISPONIBLE)
                .build());
    }

    private record Aviso(String destino, SeatResponse asiento) {
    }

    // Informativo: si un envío falla, los demás salen igual y la liberación ya quedó guardada
    private void enviar(Aviso aviso) {
        try {
            messagingTemplate.convertAndSend(aviso.destino(), aviso.asiento());
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar la liberación del asiento {}: {}", aviso.asiento().getSeatNumber(), ex.getMessage());
        }
    }

    private static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
