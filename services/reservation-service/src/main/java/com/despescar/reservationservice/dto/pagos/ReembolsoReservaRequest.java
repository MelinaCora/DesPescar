package com.despescar.reservationservice.dto.pagos;

import java.math.BigDecimal;

/** Cuerpo de POST /api/payments/internal/reservas/{reservaId}/reembolso. */
public record ReembolsoReservaRequest(BigDecimal monto, String motivo) {
}
