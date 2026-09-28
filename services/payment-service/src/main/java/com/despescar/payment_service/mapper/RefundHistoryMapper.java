package com.despescar.payment_service.mapper;

import org.springframework.stereotype.Component;

import com.despescar.payment_service.dto.response.RefundHistoryResponse;
import com.despescar.payment_service.entity.RefundHistory;

@Component
public class RefundHistoryMapper {

    public RefundHistoryResponse toResponse(RefundHistory history) {

        return RefundHistoryResponse.builder()
                .id(history.getId())
                .refundId(history.getRefund().getId())
                .status(history.getStatus())
                .changedAt(history.getChangedAt())
                .description(history.getDescription())
                .build();
    }
}