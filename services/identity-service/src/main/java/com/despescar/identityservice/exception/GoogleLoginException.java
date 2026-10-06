package com.despescar.identityservice.exception;

/** El ID token de Google no es valido, no es para esta app o el correo no esta verificado. */
public class GoogleLoginException extends RuntimeException {

    public GoogleLoginException(String message) {
        super(message);
    }
}
