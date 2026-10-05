package com.despescar.payment_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de POST /api/payments/{id}/conciliacion: el payment_id que Mercado Pago agrega a la back_url
 * (Checkout Pro, solo digitos) o el id de la orden (Checkout API via Orders, "ORD...").
 */
public record ConciliacionPagoRequest(
        @NotBlank(message = "mpPaymentId es obligatorio")
        @Pattern(regexp = "\\d{1,19}|ORD[A-Za-z0-9]{1,61}", message = "mpPaymentId debe ser el numero de pago o el id de la orden de Mercado Pago")
        String mpPaymentId) {
}
