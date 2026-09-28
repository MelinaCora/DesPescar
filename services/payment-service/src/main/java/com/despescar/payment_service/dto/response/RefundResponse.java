package com.despescar.payment_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.despescar.payment_service.enums.RefundStatus;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundResponse {

    private UUID id;

    private UUID paymentId;

    private BigDecimal amount;

    private String reason;

    private RefundStatus status;

    private String refundTransactionId;

    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

}