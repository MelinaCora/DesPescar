package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.request.RefundRequest;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
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
    private final PaymentGatewayService paymentGatewayService;
    private final RefundHistoryService refundHistoryService;

    /**
     * Creates a new refund for an approved payment.
     *
     * @param request refund request
     * @return created refund response
     */
    @Transactional
    public RefundResponse createRefund(RefundRequest request) {

        // 1. Find the payment
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: "
                                        + request.getPaymentId()
                        ));

        // 2. Validate payment status
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new InvalidPaymentStateException(
                    "Payment cannot be refunded because its current status is: "
                            + payment.getStatus()
            );
        }

        // 3. Calculate the amount already refunded
        BigDecimal refundedAmount = refundRepository
                .findByPaymentId(payment.getId())
                .stream()
                .filter(refund ->
                        refund.getStatus() == RefundStatus.PENDING
                                || refund.getStatus() == RefundStatus.APPROVED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Calculate the remaining refundable amount
        BigDecimal availableAmount = payment.getAmount()
                .subtract(refundedAmount);

        // 5. Validate requested refund amount
        if (request.getAmount().compareTo(availableAmount) > 0) {
            throw new RefundAmountExceededException(
                    "Refund amount exceeds the available refundable amount. "
                            + "Available amount: "
                            + availableAmount
            );
        }

        // 6. Create refund entity
        Refund refund = refundMapper.toEntity(request);

        refund.setPayment(payment);
        refund.setStatus(RefundStatus.PENDING);

        // 7. Save refund as PENDING
        Refund savedRefund = refundRepository.save(refund);

        // 8. Register refund history
        refundHistoryService.saveHistory(
                savedRefund,
                RefundStatus.PENDING,
                "Refund created and is pending."
        );

        // 9. Process refund through payment gateway
        RefundGatewayResponse gatewayResponse =
                paymentGatewayService.refund(
                        payment.getTransactionId(),
                        savedRefund.getAmount()
                );

        // 10. Update refund according to gateway response
        if (gatewayResponse.isApproved()) {

            savedRefund.setStatus(RefundStatus.APPROVED);

            savedRefund.setRefundTransactionId(
                    gatewayResponse.getRefundTransactionId()
            );

            savedRefund.setProcessedAt(
                    LocalDateTime.now()
            );

            refundHistoryService.saveHistory(
                    savedRefund,
                    RefundStatus.APPROVED,
                    "Refund approved by payment gateway."
            );

        } else {

            savedRefund.setStatus(RefundStatus.REJECTED);

            refundHistoryService.saveHistory(
                    savedRefund,
                    RefundStatus.REJECTED,
                    "Refund rejected by payment gateway."
            );
        }

        // 11. Save updated refund
        Refund updatedRefund = refundRepository.save(savedRefund);

        // 12. Return response
        return refundMapper.toResponse(updatedRefund);
    }

    /**
     * Gets a refund by its ID.
     *
     * @param refundId refund ID
     * @return refund response
     */
    @Transactional(readOnly = true)
    public RefundResponse getRefundById(UUID refundId) {

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() ->
                        new RefundNotFoundException(
                                "Refund not found with id: " + refundId
                        ));

        return refundMapper.toResponse(refund);
    }

    /**
     * Gets all refunds associated with a payment.
     *
     * @param paymentId payment ID
     * @return list of refund responses
     */
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPayment(UUID paymentId) {

        return refundRepository.findByPaymentId(paymentId)
                .stream()
                .map(refundMapper::toResponse)
                .toList();
    }

    /**
     * Gets all refunds associated with a user.
     *
     * @param userId user ID
     * @return list of refund responses
     */
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByUser(UUID userId) {

        return refundRepository.findByPayment_UserId(userId)
                .stream()
                .map(refundMapper::toResponse)
                .toList();
    }
}