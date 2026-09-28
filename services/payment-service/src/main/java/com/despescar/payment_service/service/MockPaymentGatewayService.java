package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.UUID;

import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import org.springframework.stereotype.Service;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.enums.PaymentMethod;

@Service
public class MockPaymentGatewayService implements PaymentGatewayService {

    @Override
    public PaymentGatewayResponse processPayment(
            BigDecimal amount,
            PaymentMethod paymentMethod) {

        String transactionId = "MOCK-" + UUID.randomUUID();

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {

            return PaymentGatewayResponse.builder()
                    .approved(false)
                    .transactionId(null)
                    .message("Payment rejected: invalid amount.")
                    .build();
        }

        return PaymentGatewayResponse.builder()
                .approved(true)
                .transactionId(transactionId)
                .message("Payment approved successfully.")
                .build();
    }

    @Override
    public RefundGatewayResponse refund(
            String transactionId,
            BigDecimal amount) {

        if (transactionId == null || transactionId.isBlank()) {

            return RefundGatewayResponse.builder()
                    .approved(false)
                    .refundTransactionId(null)
                    .message("Refund rejected: invalid transaction ID.")
                    .build();
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {

            return RefundGatewayResponse.builder()
                    .approved(false)
                    .refundTransactionId(null)
                    .message("Refund rejected: invalid amount.")
                    .build();
        }

        String refundTransactionId =
                "MOCK-REFUND-" + UUID.randomUUID();

        return RefundGatewayResponse.builder()
                .approved(true)
                .refundTransactionId(refundTransactionId)
                .message("Refund approved successfully.")
                .build();
    }
}