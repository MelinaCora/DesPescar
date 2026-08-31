package com.despescar.packageservice.exception;

public class PackageNotFoundException extends RuntimeException {

    public PackageNotFoundException(Long id) {
        super("El paquete " + id + " no existe.");
    }
}
