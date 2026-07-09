package com.despescar.flightservice.exception;

public class AirportNotFoundException extends RuntimeException {

    public AirportNotFoundException() {
        super("Airport not found.");
    }

    public AirportNotFoundException(String message) {
        super(message);
    }
}