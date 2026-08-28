package com.despescar.identityservice.exception;

public class RefreshTokenRevokedException extends RuntimeException {

    public RefreshTokenRevokedException() {
        super("Refresh token revocado");
    }
}
