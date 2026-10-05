package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentProvider;

/**
 * Proveedor simulado para desarrollo local (payments.provider=mock, el valor por defecto).
 * El checkout es una pagina del front (/pago/simulado) que aprueba o rechaza con
 * POST /api/payments/{id}/simulacion; los reembolsos se aprueban siempre.
 */
@Service
@ConditionalOnProperty(name = "payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGatewayService implements PaymentGatewayService {

    @Override
    public PaymentCheckoutResponse createCheckout(
            String paymentId,
            BigDecimal amount,
            String currency) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        return PaymentCheckoutResponse.builder()
                .preferenceId("MOCK-PREF-" + paymentId)
                .checkoutUrl("/pago/simulado?pago=" + paymentId)
                .message("Checkout simulado creado.")
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

        return RefundGatewayResponse.builder()
                .approved(true)
                .refundTransactionId("MOCK-REFUND-" + UUID.randomUUID())
                .message("Reembolso simulado aprobado.")
                .build();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }
}
