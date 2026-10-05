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
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import org.springframework.security.access.AccessDeniedException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentHistoryService {

    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentHistoryMapper paymentHistoryMapper;
    private final PaymentRepository paymentRepository;

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
    public boolean existe(UUID paymentId, String description) {
        return paymentHistoryRepository.existsByPayment_IdAndDescription(paymentId, description);
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getHistoryByPayment(UUID paymentId, Long authenticatedUserId) {
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        if (!authenticatedUserId.equals(payment.getUserId())) {
            throw new AccessDeniedException("No tienes acceso al historial de este pago.");
        }

        return paymentHistoryRepository
                .findByPayment_IdOrderByChangedAtAsc(paymentId)
                .stream()
                .map(paymentHistoryMapper::toResponse)
                .toList();
    }
}