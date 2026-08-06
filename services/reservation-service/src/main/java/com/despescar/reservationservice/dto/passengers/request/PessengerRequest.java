package com.despescar.reservationservice.dto.passengers.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PessengerRequest {

    private Long usuarioId;
    private String nombrePasajero;
    private String dniPasaporte;
}
