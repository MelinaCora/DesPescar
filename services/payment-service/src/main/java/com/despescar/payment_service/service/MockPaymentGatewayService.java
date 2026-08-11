package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.despescar.payment_service.enums.PaymentMethod;

@Service
public class MockPaymentGatewayService implements com.despescar.payment_service.services.PaymentGatewayService {

    @Override
    public String processPayment(
            BigDecimal amount,
            PaymentMethod paymentMethod) {

        return "MOCK-" + UUID.randomUUID();
    }

    @Override
    public boolean refund(String transactionId) {

        return true;
    }

}