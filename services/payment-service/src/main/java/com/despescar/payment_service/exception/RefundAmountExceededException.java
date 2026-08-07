package com.despescar.payment_service.exception;

public class RefundAmountExceededException extends RuntimeException {

    public RefundAmountExceededException(String message) {
        super(message);
    }

}