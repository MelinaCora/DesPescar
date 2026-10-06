package com.despescar.payment_service.enums;

public enum PaymentProvider {

    MERCADO_PAGO,
    /** Mercado Pago Checkout API (Orders): cobro con token de tarjeta en nuestra propia pagina. */
    MERCADO_PAGO_ORDERS,
    STRIPE,
    PAYPAL,
    MOCK

}