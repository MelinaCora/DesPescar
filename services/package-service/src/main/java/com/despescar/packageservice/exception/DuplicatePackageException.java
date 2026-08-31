package com.despescar.packageservice.exception;

public class DuplicatePackageException extends RuntimeException {

    public DuplicatePackageException(String name) {
        super("Ya existe un paquete con el nombre " + name + ".");
    }
}
