package com.despescar.reservationservice.dto.passengers.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerResponse {


    private Long id;


    private String nombreCompleto;


    private String dniPasaporte;

}
