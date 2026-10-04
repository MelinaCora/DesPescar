package com.despescar.payment_service.repository;

import com.despescar.payment_service.entity.PaymentGroup;
import com.despescar.payment_service.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentGroupRepository extends JpaRepository<PaymentGroup, UUID> {

    Optional<PaymentGroup> findByReservationId(Long reservationId);

    // Crucial para el Cron Job: buscar los grupos que ya vencieron y no se completaron
    List<PaymentGroup> findByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime now);
}