package com.despescar.reservationservice.dto;

import lombok.Data;

@Data
public class CargarPasajeroDTO {

    private Long usuarioId;
    private String nombrePasajero;
    private String dniPasaporte;

}
