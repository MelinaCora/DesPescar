package com.despescar.payment_service.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.response.RefundHistoryResponse;
import com.despescar.payment_service.entity.Refund;
import com.despescar.payment_service.entity.RefundHistory;
import com.despescar.payment_service.enums.RefundStatus;
import com.despescar.payment_service.mapper.RefundHistoryMapper;
import com.despescar.payment_service.repository.RefundHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefundHistoryService {

    private final RefundHistoryRepository refundHistoryRepository;
    private final RefundHistoryMapper refundHistoryMapper;

    @Transactional
    public void saveHistory(
            Refund refund,
            RefundStatus status,
            String description) {

        RefundHistory history = RefundHistory.builder()
                .refund(refund)
                .status(status)
                .changedAt(LocalDateTime.now())
                .description(description)
                .build();

        refundHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<RefundHistoryResponse> getHistoryByRefund(
            UUID refundId) {

        return refundHistoryRepository
                .findByRefund_IdOrderByChangedAtAsc(refundId)
                .stream()
                .map(refundHistoryMapper::toResponse)
                .toList();
    }
}