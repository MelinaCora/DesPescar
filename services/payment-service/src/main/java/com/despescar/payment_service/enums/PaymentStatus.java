package com.despescar.payment_service.enums;

public enum PaymentStatus {
    PENDING,           // Recién creado, esperando pago
    AUTHORIZED,        // Dinero congelado en la tarjeta (Fracción)
    AUTHORIZED_READY,  // Todos congelaron, listo para capturar (Grupo)
    CAPTURED,          // Dinero cobrado exitosamente
    CANCELLED,         // Pago rechazado o cancelado por falta de tiempo
    EXPIRED            // El grupo superó el límite de tiempo
}