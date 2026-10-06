package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.Locale;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;

/**
 * Lo que nos importa de una orden de Mercado Pago (POST/GET /v1/orders): el estado de la orden y
 * el de su unica transaccion. Nunca incluye el token de la tarjeta.
 */
public record OrdenMercadoPago(
        String id,
        String status,
        String statusDetail,
        String externalReference,
        BigDecimal totalAmount,
        BigDecimal totalPaidAmount,
        String transaccionId,
        String transaccionStatus,
        String transaccionStatusDetail,
        String paymentTypeId,
        String paymentMethodId) {

    public boolean aprobada() {
        return "processed".equals(estadoNormalizado());
    }

    public boolean rechazada() {
        return "failed".equals(estadoNormalizado());
    }

    /** Detalle de la transaccion (motivo de rechazo) o, si no hay, el de la orden. */
    public String detalle() {
        return transaccionStatusDetail != null ? transaccionStatusDetail : statusDetail;
    }

    private String estadoNormalizado() {
        return status == null ? "" : status.toLowerCase(Locale.ROOT);
    }

    /**
     * Estado en el vocabulario de Checkout Pro que ya entiende MercadoPagoWebhookService.aplicarEstado:
     * approved, rejected, cancelled, refunded, charged_back, authorized o in_process.
     */
    public String estadoComoPago() {
        return switch (estadoNormalizado()) {
            case "processed" -> "approved";
            case "failed" -> "rejected";
            case "canceled", "cancelled", "expired" -> "cancelled";
            case "refunded" -> "refunded";
            case "charged_back" -> "charged_back";
            case "action_required" -> "waiting_capture".equals(statusDetail) ? "authorized" : "in_process";
            default -> "in_process";
        };
    }

    public PaymentGatewayResponse toGatewayResponse() {
        BigDecimal monto = totalPaidAmount != null && totalPaidAmount.signum() > 0 ? totalPaidAmount : totalAmount;
        return PaymentGatewayResponse.builder()
                .approved(aprobada())
                .transactionId(id)
                .externalReference(externalReference)
                .status(estadoComoPago())
                .currency("ARS")
                .amount(monto)
                .paymentTypeId(paymentTypeId)
                .paymentMethodId(paymentMethodId)
                .message(detalle())
                .build();
    }
}
