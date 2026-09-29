package com.despescar.payment_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentStatus;

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
public class PaymentResponse {

    private UUID id;

    private Long reservationId;

    private Long userId;

    private BigDecimal amount;

    private PaymentStatus status;

    private PaymentMethod paymentMethod;

    private String transactionId;

    private String preferenceId;

    private String checkoutUrl;

    private LocalDateTime paymentDate;

    private String currency;

    private LocalDateTime createdAt;

}