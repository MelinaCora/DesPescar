package com.despescar.packageservice.exception;

public class PackageInactiveException extends RuntimeException {

    public PackageInactiveException(Long id) {
        super("El paquete " + id + " esta inactivo. Debes reactivarlo antes de modificarlo.");
    }
}
