package com.despescar.identityservice.exception;

public class ExpiredRefreshTokenException extends RuntimeException {

    public ExpiredRefreshTokenException() {
        super("Refresh token expirado");
    }
}
