package com.despescar.payment_service.services;

import java.math.BigDecimal;

import com.despescar.payment_service.enums.PaymentMethod;

public interface PaymentGatewayService {

    String processPayment(
            BigDecimal amount,
            PaymentMethod paymentMethod);

    boolean refund(String transactionId);

}