package com.despescar.payment_service.mapper;

import org.springframework.stereotype.Component;

import com.despescar.payment_service.dto.request.RefundRequest;
import com.despescar.payment_service.dto.response.RefundResponse;
import com.despescar.payment_service.entity.Refund;

@Component
public class RefundMapper {

    public Refund toEntity(RefundRequest request) {

        return Refund.builder()
                .amount(request.getAmount())
                .reason(request.getReason())
                .build();
    }

    public RefundResponse toResponse(Refund refund) {

        return RefundResponse.builder()
                .id(refund.getId())
                .paymentId(refund.getPayment().getId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .refundTransactionId(refund.getRefundTransactionId())
                .createdAt(refund.getCreatedAt())
                .processedAt(refund.getProcessedAt())
                .build();
    }
}