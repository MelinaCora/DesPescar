package com.despescar.flightservice.exception;

public class AirlineNotFoundException extends RuntimeException {

    public AirlineNotFoundException() {
        super("Airline not found.");
    }

    public AirlineNotFoundException(String message) {
        super(message);
    }
}