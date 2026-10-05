package com.despescar.reservationservice.dto.reservation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

/** Lo que manda payment-service al aprobarse un pago (contrato C3). */
@Data
public class PaymentConfirmationRequest {

    @NotNull
    private Long pagadorId;

    /** Identifica el pago: el mismo token reenviado es idempotente, otro distinto es un duplicado. */
    @NotBlank
    @Size(max = 120)
    private String tokenPago;

    /** Monto efectivamente cobrado; tiene que coincidir con el montoTotal del carrito (D7). */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal monto;
}
