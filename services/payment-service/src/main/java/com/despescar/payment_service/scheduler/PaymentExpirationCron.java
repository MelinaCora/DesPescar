package com.despescar.payment_service.scheduler;

import com.despescar.payment_service.entity.PaymentFraction;
import com.despescar.payment_service.entity.PaymentGroup;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.repository.PaymentGroupRepository;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCancelRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationCron {

    private final PaymentGroupRepository groupRepository;

    // Se ejecuta cada 1 minuto
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelExpiredGroups() {
        // Busca grupos pendientes que ya pasaron su fecha de expiración
        List<PaymentGroup> expiredGroups = groupRepository
                .findByStatusAndExpiresAtBefore(PaymentStatus.PENDING, LocalDateTime.now());

        if (expiredGroups.isEmpty()) {
            return;
        }

        PaymentClient client = new PaymentClient();

        for (PaymentGroup group : expiredGroups) {
            log.info("Cancelando Grupo {} por tiempo expirado.", group.getId());

            for (PaymentFraction fraction : group.getFractions()) {
                if (fraction.getStatus().equals(PaymentStatus.AUTHORIZED)) {
                    try {
                        Long mpId = Long.valueOf(fraction.getMpPaymentId());
                        // Cancela la retención en Mercado Pago, liberando la tarjeta al instante
                        client.cancel(mpId);

                        fraction.setStatus(PaymentStatus.CANCELLED);
                        log.info("Retención cancelada para la fracción {}", fraction.getId());
                    } catch (MPException | MPApiException e) {
                        log.error("Error al cancelar en MP la fracción {}: {}", fraction.getMpPaymentId(), e.getMessage());
                    }
                } else if (fraction.getStatus().equals(PaymentStatus.PENDING)) {
                    fraction.setStatus(PaymentStatus.CANCELLED);
                }
            }

            group.setStatus(PaymentStatus.EXPIRED);
            groupRepository.save(group);

            // ---> AQUÍ LLAMAS A TU MICROSERVICIO DE RESERVAS <---
            // Ej: reservationClient.releaseSeats(group.getReservationId());
            log.info("Asientos liberados para la reserva {}.", group.getReservationId());
        }
    }
}