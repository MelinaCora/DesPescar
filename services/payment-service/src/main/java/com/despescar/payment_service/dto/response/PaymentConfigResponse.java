package com.despescar.payment_service.dto.response;

import com.despescar.payment_service.enums.PaymentProvider;

/** GET /api/payments/config: proveedor activo y, con Mercado Pago Orders, la public key para MercadoPago.js. */
public record PaymentConfigResponse(PaymentProvider provider, String publicKey) {
}
