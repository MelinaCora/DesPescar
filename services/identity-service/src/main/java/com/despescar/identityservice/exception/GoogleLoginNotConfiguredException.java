package com.despescar.identityservice.exception;

/** Falta GOOGLE_CLIENT_ID: el ingreso con Google no esta habilitado en este entorno. */
public class GoogleLoginNotConfiguredException extends RuntimeException {

    public GoogleLoginNotConfiguredException() {
        super("El ingreso con Google no esta disponible en este momento");
    }
}
