package com.despescar.payment_service.dto.request;

import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/payments/internal/grupos/{reservaId}/reembolsos (contrato CB5). */
public record ReembolsoGrupoRequest(@Size(max = 60) String motivo) {
}
