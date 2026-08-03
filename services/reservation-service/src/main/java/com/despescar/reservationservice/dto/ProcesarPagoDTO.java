package com.despescar.reservationservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProcesarPagoDTO {

    private Long pagadorId;
    private String tokenPago;

}
