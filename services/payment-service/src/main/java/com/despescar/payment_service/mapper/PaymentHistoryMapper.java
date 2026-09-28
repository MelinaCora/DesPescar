package com.despescar.payment_service.mapper;

import org.springframework.stereotype.Component;

import com.despescar.payment_service.dto.response.PaymentHistoryResponse;
import com.despescar.payment_service.entity.PaymentHistory;

@Component
public class PaymentHistoryMapper {

    public PaymentHistoryResponse toResponse(PaymentHistory history) {

        return PaymentHistoryResponse.builder()
                .id(history.getId())
                .paymentId(history.getPayment().getId())
                .status(history.getStatus())
                .changedAt(history.getChangedAt())
                .description(history.getDescription())
                .build();
    }
}