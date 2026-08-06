package com.despescar.payment_service.mapper;

import org.springframework.stereotype.Component;

import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;

@Component
public class PaymentMapper {

    public Payment toEntity(PaymentRequest request) {

        return Payment.builder()
                .reservationId(request.getReservationId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .currency(request.getCurrency())
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
                .paymentDate(payment.getPaymentDate())
                .currency(payment.getCurrency())
                .build();
    }
}
