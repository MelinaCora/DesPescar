package com.despescar.hotelservice.exception;

/** Pedido con datos que no tienen sentido (fechas, política, huéspedes). Responde 400. */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String message) {
        super(message);
    }
}
