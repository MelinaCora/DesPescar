package com.despescar.payment_service.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.response.PaymentHistoryResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.entity.PaymentHistory;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.mapper.PaymentHistoryMapper;
import com.despescar.payment_service.repository.PaymentHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentHistoryService {

    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentHistoryMapper paymentHistoryMapper;

    @Transactional
    public void saveHistory(
            Payment payment,
            PaymentStatus status,
            String description) {

        PaymentHistory history = PaymentHistory.builder()
                .payment(payment)
                .status(status)
                .changedAt(LocalDateTime.now())
                .description(description)
                .build();

        paymentHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getHistoryByPayment(UUID paymentId) {

        return paymentHistoryRepository
                .findByPayment_IdOrderByChangedAtAsc(paymentId)
                .stream()
                .map(paymentHistoryMapper::toResponse)
                .toList();
    }
}