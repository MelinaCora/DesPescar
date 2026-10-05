package com.despescar.payment_service.exception;

/** El proveedor de pagos (Mercado Pago) fallo o no respondio. Se responde 502. */
public class ProveedorPagoException extends RuntimeException {

    public ProveedorPagoException(String message, Throwable cause) {
        super(message, cause);
    }
}
