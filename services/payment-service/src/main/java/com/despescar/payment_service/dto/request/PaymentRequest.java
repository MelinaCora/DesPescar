package com.despescar.payment_service.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import com.despescar.payment_service.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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

public class PaymentRequest {
	//entrada de datos
    @NotNull(message = "Reservation ID is required")
    private UUID reservationId;

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Currency is required")
    @Pattern(
            regexp = "^[A-Z]{3}$",
            message = "Currency must be a valid ISO 4217 code (e.g. ARS, USD, EUR)"
    )
    private String currency;


}
