package com.despescar.identityservice.exception;

public class RefreshTokenNotFoundException extends RuntimeException {

    public RefreshTokenNotFoundException() {
        super("Refresh token no encontrado");
    }
}
