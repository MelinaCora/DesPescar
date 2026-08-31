package com.despescar.flightservice.exception;

public class AirportCodeAlreadyExistException extends RuntimeException {

    public AirportCodeAlreadyExistException(){
        super("Airport code already exists.");
    }

    public AirportCodeAlreadyExistException(String message){
        super (message);
    }
}
