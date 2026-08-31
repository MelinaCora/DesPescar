package com.despescar.flightservice.exception;

public class FlightAlreadyExistsException extends RuntimeException {

    public FlightAlreadyExistsException() {
        super("Flight already exists.");
    }

    public FlightAlreadyExistsException(String message) {
        super(message);
    }
}