package com.despescar.payment_service.repository;

import com.despescar.payment_service.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {

    List<PaymentHistory> findByPayment_IdOrderByChangedAtAsc(UUID paymentId);

}