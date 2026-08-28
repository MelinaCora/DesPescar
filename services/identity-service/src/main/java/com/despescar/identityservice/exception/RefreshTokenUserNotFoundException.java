package com.despescar.identityservice.exception;

public class RefreshTokenUserNotFoundException extends RuntimeException {

    public RefreshTokenUserNotFoundException() {
        super("Usuario asociado al refresh token no encontrado");
    }
}
