package com.despescar.payment_service.dto.response;

/**
 * Resultado de POST /api/payments/{id}/orden: el pago actualizado y el detalle que informo Mercado
 * Pago (status_detail de la transaccion, por ejemplo insufficient_amount), para que el front lo
 * traduzca.
 */
public record OrdenPagoResponse(PaymentResponse pago, String estadoOrden, String detalle) {
}
