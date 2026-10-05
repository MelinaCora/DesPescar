package com.despescar.hotelservice.exception;

import java.util.UUID;

public class RetencionNoEncontradaException extends RuntimeException {
    public RetencionNoEncontradaException(UUID id) {
        super("No existe la retención " + id + ".");
    }
}
