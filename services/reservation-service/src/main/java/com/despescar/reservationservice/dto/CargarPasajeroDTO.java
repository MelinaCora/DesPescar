package com.despescar.reservationservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CargarPasajeroDTO {

    private Long usuarioId;
    private String nombrePasajero;
    private String dniPasaporte;

}
