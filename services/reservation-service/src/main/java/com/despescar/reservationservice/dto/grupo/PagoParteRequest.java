package com.despescar.reservationservice.dto.grupo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Lo que manda payment-service al aprobarse el cobro de una parte (CB3). */
public record PagoParteRequest(
        @NotNull Long pagadorId,
        @NotBlank @Size(max = 120) String tokenPago,
        @NotNull @DecimalMin("0.01") BigDecimal monto) {
}
