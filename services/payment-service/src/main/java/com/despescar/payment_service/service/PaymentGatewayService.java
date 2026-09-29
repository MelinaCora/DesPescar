package com.despescar.payment_service.service;

import java.math.BigDecimal;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;

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
}
