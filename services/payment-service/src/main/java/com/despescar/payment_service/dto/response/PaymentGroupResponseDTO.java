package com.despescar.payment_service.dto.response;

import com.despescar.payment_service.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PaymentGroupResponseDTO {
    private UUID groupId;
    private Long reservationId;
    private BigDecimal totalAmount;
    private PaymentStatus status;
    private LocalDateTime expiresAt;
    private List<FractionResponseDTO> fractions;
}

