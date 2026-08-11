package com.despescar.payment_service.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.despescar.payment_service.service.PaymentHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentHistoryService paymentHistoryService;

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        Payment payment = paymentMapper.toEntity(request);

        payment.setStatus(PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.save(payment);

        paymentHistoryService.saveHistory(
                savedPayment,
                PaymentStatus.PENDING,
                "Payment created and is pending."
        );

        return paymentMapper.toResponse(savedPayment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + paymentId
                        ));

        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByUser(UUID userId) {

        return paymentRepository.findByUserId(userId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByReservation(UUID reservationId) {

        return paymentRepository.findByReservationId(reservationId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional
    public PaymentResponse cancelPayment(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + paymentId
                        ));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    "Payment cannot be cancelled because its current status is: "
                            + payment.getStatus()
            );
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setPaymentDate(LocalDateTime.now());

        Payment updatedPayment = paymentRepository.save(payment);

        paymentHistoryService.saveHistory(
                updatedPayment,
                PaymentStatus.CANCELLED,
                "Payment cancelled."
        );

        return paymentMapper.toResponse(updatedPayment);
    }

}