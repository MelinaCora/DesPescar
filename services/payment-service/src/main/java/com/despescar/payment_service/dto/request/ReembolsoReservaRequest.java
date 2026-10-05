package com.despescar.payment_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Cuerpo de POST /api/payments/internal/reservas/{reservaId}/reembolso. */
public record ReembolsoReservaRequest(@NotNull @DecimalMin("0.01") BigDecimal monto, @Size(max = 60) String motivo) {
}
