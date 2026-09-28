package com.despescar.payment_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.despescar.payment_service.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByReservationId(UUID reservationId);

    List<Payment> findByUserId(UUID userId);

    List<Payment> findByStatus(PaymentStatus status);

    Optional<Payment> findByTransactionId(String transactionId);

    List<PaymentHistory> findByPaymentIdOrderByChangedAtAsc(UUID paymentId);

}