package com.despescar.payment_service.mapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;

@Component
public class PaymentMapper {

    public Payment toEntity(
            PaymentRequest request,
            BigDecimal amount,
            String currency) {

        return Payment.builder()
                .reservationId(request.getReservationId())
                .userId(request.getUserId())
                .amount(amount)
                .currency(currency)
                .provider(PaymentProvider.MERCADO_PAGO)
                .build();
    }

    public PaymentResponse toResponse(Payment payment) {

        return PaymentResponse.builder()
                .id(payment.getId())
                .reservationId(payment.getReservationId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .preferenceId(payment.getPreferenceId())
                .checkoutUrl(payment.getCheckoutUrl())
                .paymentDate(payment.getPaymentDate())
                .currency(payment.getCurrency())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
