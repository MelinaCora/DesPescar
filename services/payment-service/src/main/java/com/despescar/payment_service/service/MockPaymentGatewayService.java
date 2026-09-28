package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.UUID;

import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.enums.PaymentMethod;

public class MockPaymentGatewayService implements PaymentGatewayService {

    @Override
    public PaymentCheckoutResponse createCheckout(
            String paymentId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        return PaymentCheckoutResponse.builder()
                .preferenceId("MOCK-PREF-" + UUID.randomUUID())
                .checkoutUrl("https://mock-gateway.test/checkout/" + paymentId)
                .message("Checkout created successfully.")
                .build();
    }

    @Override
    public PaymentGatewayResponse getPaymentStatus(String transactionId) {

        if (transactionId == null || transactionId.isBlank()) {
            return PaymentGatewayResponse.builder()
                    .approved(false)
                    .transactionId(null)
                    .status("rejected")
                    .message("Payment rejected: invalid transaction ID.")
                    .build();
        }

        return PaymentGatewayResponse.builder()
                .approved(true)
                .transactionId(transactionId)
                .externalReference(transactionId)
                .status("approved")
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