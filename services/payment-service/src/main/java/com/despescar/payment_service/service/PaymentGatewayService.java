package com.despescar.payment_service.service;

import java.math.BigDecimal;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentProvider;

/**
 * Proveedor de pagos. Hay uno solo activo, elegido con {@code payments.provider}
 * (mock por defecto, o mercadopago).
 */
public interface PaymentGatewayService {

    PaymentCheckoutResponse createCheckout(
            String paymentId,
            BigDecimal amount,
            String currency);

    PaymentGatewayResponse getPaymentStatus(
            String transactionId);

    RefundGatewayResponse refund(
            String transactionId,
            BigDecimal amount);

    /** Proveedor que implementa esta pasarela; se guarda en cada pago. */
    PaymentProvider provider();
}
