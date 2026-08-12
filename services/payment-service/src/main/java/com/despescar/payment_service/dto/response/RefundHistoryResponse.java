package com.despescar.payment_service.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.despescar.payment_service.enums.RefundStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundHistoryResponse {

    private UUID id;

    private UUID refundId;

    private RefundStatus status;

    private LocalDateTime changedAt;

    private String description;
}