package com.despescar.reservationservice.dto;

import lombok.Data;

@Data
public class ProcesarPagoDTO {

    private Long pagadorId;
    private String tokenPago;

}
