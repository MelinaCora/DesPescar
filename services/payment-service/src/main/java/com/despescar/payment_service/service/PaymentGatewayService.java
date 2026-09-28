package com.despescar.payment_service.service;

import java.math.BigDecimal;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentMethod;

public interface PaymentGatewayService {

    PaymentCheckoutResponse createCheckout(
            String paymentId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod);

    PaymentGatewayResponse getPaymentStatus(
            String transactionId);

    RefundGatewayResponse refund(
            String transactionId,
            BigDecimal amount);
}

