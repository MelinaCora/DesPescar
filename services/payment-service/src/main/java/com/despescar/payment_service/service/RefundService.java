package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.request.RefundRequest;
import com.despescar.payment_service.dto.response.RefundResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.entity.Refund;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.enums.RefundStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.exception.RefundAmountExceededException;
import com.despescar.payment_service.exception.RefundNotFoundException;
import com.despescar.payment_service.mapper.RefundMapper;
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.repository.RefundRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundMapper refundMapper;

    @Transactional
    public RefundResponse createRefund(RefundRequest request) {

        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: "
                                        + request.getPaymentId()
                        ));

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new InvalidPaymentStateException(
                    "Payment cannot be refunded because its current status is: "
                            + payment.getStatus()
            );
        }

        BigDecimal refundedAmount = refundRepository
                .findByPaymentId(payment.getId())
                .stream()
                .filter(refund ->
                        refund.getStatus() == RefundStatus.PENDING
                                || refund.getStatus() == RefundStatus.APPROVED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableAmount = payment.getAmount()
                .subtract(refundedAmount);

        if (request.getAmount().compareTo(availableAmount) > 0) {
            throw new RefundAmountExceededException(
                    "Refund amount exceeds the available refundable amount. "
                            + "Available: " + availableAmount
            );
        }

        Refund refund = refundMapper.toEntity(request);

        refund.setPayment(payment);
        refund.setStatus(RefundStatus.PENDING);

        Refund savedRefund = refundRepository.save(refund);

        return refundMapper.toResponse(savedRefund);
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefundById(UUID refundId) {

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() ->
                        new RefundNotFoundException(
                                "Refund not found with id: " + refundId
                        ));

        return refundMapper.toResponse(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPayment(UUID paymentId) {

        return refundRepository.findByPaymentId(paymentId)
                .stream()
                .map(refundMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByUser(UUID userId) {

        return refundRepository.findByPayment_UserId(userId)
                .stream()
                .map(refundMapper::toResponse)
                .toList();
    }

}