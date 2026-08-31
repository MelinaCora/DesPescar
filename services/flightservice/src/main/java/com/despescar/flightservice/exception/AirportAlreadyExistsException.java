package com.despescar.flightservice.exception;

public class AirportAlreadyExistsException extends RuntimeException {

    public AirportAlreadyExistsException() {
        super("Airport already exists.");
    }

    public AirportAlreadyExistsException(String message) {
        super(message);
    }
}