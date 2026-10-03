package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.despescar.payment_service.dto.request.RefundRequest;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.dto.response.RefundResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.entity.Refund;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.enums.RefundStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.exception.RefundAmountExceededException;
import com.despescar.payment_service.mapper.RefundMapper;
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.repository.RefundRepository;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @Mock
    private RefundHistoryService refundHistoryService;

    @InjectMocks
    private RefundService refundService;

    @Test
    void createRefundShouldApproveRefundWhenGatewayApproves() {
        Payment payment = approvedPayment();
        RefundRequest request = refundRequest(payment.getId(), new BigDecimal("30.00"));
        Refund refund = Refund.builder()
                .amount(request.getAmount())
                .reason(request.getReason())
                .build();

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(payment.getId())).thenReturn(List.of());
        when(refundMapper.toEntity(request)).thenReturn(refund);
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund savedRefund = invocation.getArgument(0);
            if (savedRefund.getId() == null) {
                savedRefund.setId(UUID.randomUUID());
            }
            return savedRefund;
        });
        when(paymentGatewayService.refund(payment.getTransactionId(), request.getAmount()))
                .thenReturn(RefundGatewayResponse.builder()
                        .approved(true)
                        .refundTransactionId("refund-123")
                        .message("ok")
                        .build());
        when(refundMapper.toResponse(any(Refund.class))).thenAnswer(invocation -> {
            Refund savedRefund = invocation.getArgument(0);
            return RefundResponse.builder()
                    .id(savedRefund.getId())
                    .paymentId(payment.getId())
                    .amount(savedRefund.getAmount())
                    .reason(savedRefund.getReason())
                    .status(savedRefund.getStatus())
                    .refundTransactionId(savedRefund.getRefundTransactionId())
                    .createdAt(savedRefund.getCreatedAt())
                    .processedAt(savedRefund.getProcessedAt())
                    .build();
        });

        RefundResponse response = refundService.createRefund(request, 55L);

        assertThat(response.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(response.getRefundTransactionId()).isEqualTo("refund-123");
        verify(refundRepository, times(2)).save(any(Refund.class));
        verify(refundHistoryService).saveHistory(any(Refund.class), org.mockito.ArgumentMatchers.eq(RefundStatus.PENDING), org.mockito.ArgumentMatchers.eq("Refund created and is pending."));
        verify(refundHistoryService).saveHistory(any(Refund.class), org.mockito.ArgumentMatchers.eq(RefundStatus.APPROVED), org.mockito.ArgumentMatchers.eq("Refund approved by payment gateway."));
    }

    @Test
    void createRefundShouldRejectWhenPaymentIsNotApproved() {
        Payment payment = approvedPayment();
        payment.setStatus(PaymentStatus.PENDING);
        RefundRequest request = refundRequest(payment.getId(), new BigDecimal("10.00"));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> refundService.createRefund(request, 55L))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessage("Payment cannot be refunded because its current status is: PENDING");
    }

    @Test
    void createRefundShouldRejectWhenAmountExceedsAvailableBalance() {
        Payment payment = approvedPayment();
        RefundRequest request = refundRequest(payment.getId(), new BigDecimal("80.00"));
        Refund existingRefund = Refund.builder()
                .amount(new BigDecimal("30.00"))
                .status(RefundStatus.APPROVED)
                .payment(payment)
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(payment.getId())).thenReturn(List.of(existingRefund));

        assertThatThrownBy(() -> refundService.createRefund(request, 55L))
                .isInstanceOf(RefundAmountExceededException.class)
                .hasMessageContaining("Available amount: 70.00");
    }

    @Test
    void getRefundByIdShouldThrowWhenRefundDoesNotExist() {
        UUID refundId = UUID.randomUUID();
        when(refundRepository.findById(refundId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.getRefundById(refundId, 55L))
                .isInstanceOf(com.despescar.payment_service.exception.RefundNotFoundException.class)
                .hasMessage("Refund not found with id: " + refundId);
    }

    private RefundRequest refundRequest(UUID paymentId, BigDecimal amount) {
        return RefundRequest.builder()
                .paymentId(paymentId)
                .amount(amount)
                .reason("Customer requested cancellation")
                .build();
    }

    private Payment approvedPayment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .reservationId(77L)
                .userId(55L)
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("ARS")
                .transactionId("mp-123")
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
