package com.despescar.payment_service.client.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Cuerpo de POST /api/bookings/internal/{id}/payment-confirmed (contrato C3). */
@Getter
@Builder
@AllArgsConstructor
public class ProcessPaymentRequest {

    private final Long pagadorId;
    private final String tokenPago;
    /** Monto efectivamente cobrado; reservation-service lo compara con el montoTotal (D7). */
    private final BigDecimal monto;
}
