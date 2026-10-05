package com.despescar.payment_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de POST /api/payments/{id}/conciliacion: el payment_id que Mercado Pago agrega a la back_url. */
public record ConciliacionPagoRequest(
        @NotBlank(message = "mpPaymentId es obligatorio")
        @Pattern(regexp = "\\d{1,19}", message = "mpPaymentId debe ser el numero de pago de Mercado Pago")
        String mpPaymentId) {
}
