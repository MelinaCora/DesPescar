package com.despescar.payment_service.dto.response;

import com.despescar.payment_service.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class FractionResponseDTO {
    private UUID fractionId;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status; // Así React sabe si poner un check verde o el botón de "Pagar"
}
