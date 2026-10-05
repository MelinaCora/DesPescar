package com.despescar.payment_service.service;

import java.math.BigDecimal;

/**
 * Operaciones de Mercado Pago Checkout API (Orders) que no entran en PaymentGatewayService: cobrar
 * con un token de tarjeta y releer la orden. Solo existe con payments.provider=mercadopago_orders.
 */
public interface MercadoPagoOrdenGateway {

    /** Lo que hace falta para crear la orden; el token nunca se registra ni se guarda. */
    record CrearOrden(
            String paymentId,
            BigDecimal amount,
            String token,
            String paymentMethodId,
            String paymentTypeId,
            int installments,
            String payerEmail) {
    }

    OrdenMercadoPago crearOrden(CrearOrden pedido);

    OrdenMercadoPago consultarOrden(String ordenId);

    /** Public key para inicializar MercadoPago.js en el front. */
    String publicKey();
}
