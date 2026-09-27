package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingScheduler {

    private final BookingRepository bookingRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final ReservationMapper reservationMapper;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void verificarCarritosExpirados() {

        LocalDateTime ahora = LocalDateTime.now();

        List<Reservation> reservasExpiradas = bookingRepository.findAll().stream()
                .filter(r -> r.getEstado() == ReservationStatus.INICIADA ||
                        r.getEstado() == ReservationStatus.ESPERANDO_PAGADORES ||
                        r.getEstado() == ReservationStatus.PENDIENTE_PAGO)
                .filter(r -> ahora.isAfter(r.getLimiteTiempo()))
                .collect(Collectors.toList());

        if (reservasExpiradas.isEmpty()) {
            return;
        }

        for (Reservation reservation : reservasExpiradas) {

            reservation.setEstado(ReservationStatus.EXPIRADA);

            reservation.getDetalles().forEach(detalle -> {
                if (PaymentStatus.PAGADO.equals(detalle.getPaymentStatus())) {
                    detalle.setPaymentStatus(PaymentStatus.REEMBOLSADO);
                } else {
                    detalle.setPaymentStatus(PaymentStatus.CANCELADO);
                }
            });

            bookingRepository.save(reservation);

            log.warn("Cron Job: La reserva ID {} expiró por inactividad. Estado actualizado a EXPIRADA.", reservation.getId());

            ReservationResponse responseExpirada = reservationMapper.toResponse(reservation);
            messagingTemplate.convertAndSend("/topic/reserva/" + reservation.getId(), responseExpirada);
        }
    }
}