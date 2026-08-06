package com.despescar.reservationservice.dto.reservation.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProcessPaymentRequest {

    private Long pagadorId;
    private String tokenPago;

}
