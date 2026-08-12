package com.despescar.payment_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.despescar.payment_service.entity.RefundHistory;

public interface RefundHistoryRepository
        extends JpaRepository<RefundHistory, UUID> {

    List<RefundHistory> findByRefund_IdOrderByChangedAtAsc(UUID refundId);

}