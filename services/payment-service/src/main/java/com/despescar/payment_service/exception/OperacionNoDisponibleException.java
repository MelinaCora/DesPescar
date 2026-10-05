package com.despescar.payment_service.exception;

/** La operacion no existe con el proveedor activo (simulacion sin mock, conciliacion sin Mercado Pago). Se responde 404. */
public class OperacionNoDisponibleException extends RuntimeException {

    public OperacionNoDisponibleException(String message) {
        super(message);
    }
}
