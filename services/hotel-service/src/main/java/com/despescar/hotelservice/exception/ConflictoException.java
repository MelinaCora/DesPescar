package com.despescar.hotelservice.exception;

/** El pedido es válido pero choca con el estado actual (sin lugar, retención liberada). Responde 409. */
public class ConflictoException extends RuntimeException {

    private final String codigo;

    public ConflictoException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
