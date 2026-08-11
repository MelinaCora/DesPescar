package com.despescar.payment_service.service;

import java.math.BigDecimal;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.enums.PaymentMethod;

public interface PaymentGatewayService {

    PaymentGatewayResponse processPayment(
            BigDecimal amount,
            PaymentMethod paymentMethod);

    boolean refund(String transactionId);

}