package com.despescar.payment_service.dto.request;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de POST /api/payments/{id}/simulacion (solo proveedor mock). */
public record SimulacionPagoRequest(
        @NotNull(message = "aprobado es obligatorio") Boolean aprobado) {
}
