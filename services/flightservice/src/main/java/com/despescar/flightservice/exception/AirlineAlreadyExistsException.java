package com.despescar.flightservice.exception;

public class AirlineAlreadyExistsException extends RuntimeException {

    public AirlineAlreadyExistsException() {
        super("Airline already exists.");
    }

    public AirlineAlreadyExistsException(String message) {
        super(message);
    }
}