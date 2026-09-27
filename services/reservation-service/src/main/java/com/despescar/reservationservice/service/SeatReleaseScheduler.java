package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.reservation.response.SeatResponse;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatReleaseScheduler {

    private final SeatRepository seatRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void liberarAsientosExpirados() {
        LocalDateTime ahora = LocalDateTime.now();

        List<Seat> asientosExpirados = seatRepository.findByStatusSeatAndBloqueadoHastaBefore("RESERVADO_TEMPORAL", ahora);

        if (asientosExpirados.isEmpty()) {
            return;
        }

        log.warn("⏳ Liberando {} asientos por inactividad en el WebSocket...", asientosExpirados.size());

        for (Seat seat : asientosExpirados) {
            seat.setStatusSeat("DISPONIBLE");
            seat.setBlockedByUserId(null);
            seat.setBloqueadoHasta(null);

            seatRepository.save(seat);

            SeatResponse response = SeatResponse.builder()
                    .seatNumber(seat.getNumberSeat())
                    .seatStatus("DISPONIBLE")
                    .build();

            messagingTemplate.convertAndSend("/topic/flight/" + seat.getFlightId(), response);
        }
    }
}