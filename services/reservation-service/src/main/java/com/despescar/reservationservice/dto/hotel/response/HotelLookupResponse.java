package com.despescar.reservationservice.dto.hotel.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Hotel-Service devuelve mas campos (nombre, ciudad, direccion, etc.) que no
 * necesitamos aqui. Se ignoran para no romper la deserializacion.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelLookupResponse {

    private UUID id;
}

