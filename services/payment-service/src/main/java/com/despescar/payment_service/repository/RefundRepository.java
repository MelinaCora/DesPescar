package com.despescar.payment_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.despescar.payment_service.entity.Refund;
import com.despescar.payment_service.enums.RefundStatus;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByPaymentId(UUID paymentId);

    List<Refund> findByStatus(RefundStatus status);

    List<Refund> findByPaymentUserId(UUID userId);

    List<Refund> findByPaymentReservationId(UUID reservationId);

    List<Refund> findByRefundTransactionId(String refundTransactionId);

}