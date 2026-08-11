package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.UUID;

import com.despescar.payment_service.service.PaymentGatewayService;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.enums.PaymentMethod;

@Service
public class MockPaymentGatewayService implements PaymentGatewayService {

    @Override
    public PaymentGatewayResponse processPayment(
            @NonNull BigDecimal amount,
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