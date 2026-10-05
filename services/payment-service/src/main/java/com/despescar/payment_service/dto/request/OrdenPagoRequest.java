package com.despescar.payment_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/payments/{id}/orden: lo que devuelve el Card Payment Brick de MercadoPago.js.
 * El token representa la tarjeta (un solo uso); los datos de la tarjeta nunca llegan al back.
 */
public record OrdenPagoRequest(
        @NotBlank(message = "token es obligatorio")
        @Size(max = 128, message = "token invalido")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "token invalido")
        String token,

        @NotBlank(message = "paymentMethodId es obligatorio")
        @Size(max = 40, message = "paymentMethodId invalido")
        @Pattern(regexp = "[a-z0-9_-]+", message = "paymentMethodId invalido")
        String paymentMethodId,

        @Pattern(regexp = "credit_card|debit_card|prepaid_card", message = "paymentTypeId invalido")
        String paymentTypeId,

        @Min(value = 1, message = "installments debe ser al menos 1")
        @Max(value = 36, message = "installments no puede superar 36")
        Integer installments,

        @Email(message = "payerEmail invalido")
        @Size(max = 120, message = "payerEmail invalido")
        String payerEmail) {

    public int cuotas() {
        return installments == null ? 1 : installments;
    }

    public String tipo() {
        return paymentTypeId == null ? "credit_card" : paymentTypeId;
    }
}
