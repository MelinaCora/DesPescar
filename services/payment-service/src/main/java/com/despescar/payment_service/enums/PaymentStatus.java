package com.despescar.payment_service.enums;

public enum PaymentStatus {
	
	//definimos los distintos estados que puede tener el pago
    PENDING,
    AUTHORIZED,
    APPROVED,
    REJECTED,
    REFUNDED,
    CANCELLED,
}
