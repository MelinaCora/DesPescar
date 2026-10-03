package com.despescar.reservationservice.dto.reservation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentConfirmationRequest {

    @NotNull
    private Long pagadorId;

    @NotBlank
    private String tokenPago;
}