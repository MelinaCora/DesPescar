package com.despescar.payment_service.service;

import java.math.BigDecimal;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentMethod;

public interface PaymentGatewayService {

    PaymentGatewayResponse processPayment(
            BigDecimal amount,
            PaymentMethod paymentMethod);

    RefundGatewayResponse refund(
            String transactionId,
            BigDecimal amount);
}