package com.despescar.payment_service.exception;

public class ReservationClientException extends RuntimeException {

    public ReservationClientException(String message) {
        super(message);
    }

    public ReservationClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
