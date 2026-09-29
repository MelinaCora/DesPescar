package com.despescar.payment_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ProcessPaymentRequest {

    private final Long pagadorId;
    private final String tokenPago;
}
