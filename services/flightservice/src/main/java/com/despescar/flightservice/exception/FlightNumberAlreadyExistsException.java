package com.despescar.flightservice.exception;

public class FlightNumberAlreadyExistsException extends RuntimeException {

    public FlightNumberAlreadyExistsException() {
      super("Flight number already exists. ");
    }
    public FlightNumberAlreadyExistsException(String message) {
        super(message);
    }
}
