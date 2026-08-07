package com.despescar.payment_service.exception;

public class RefundAlreadyProcessedException extends RuntimeException {

    public RefundAlreadyProcessedException(String message) {
        super(message);
    }

}