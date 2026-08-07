package com.despescar.reservationservice.dto.extraBaggage.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ExtraBaggageResponse {

    private Long id;

    private Long detalleReservaId;

    private Double peso;

    private BigDecimal precio;

}