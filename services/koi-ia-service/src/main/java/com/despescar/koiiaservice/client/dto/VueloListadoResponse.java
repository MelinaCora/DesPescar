package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lo que KOI usa de GET /api/flights (flightservice, público): la ruta y el día de cada vuelo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VueloListadoResponse(Aeropuerto originAirport, Aeropuerto destinationAirport, String departureTime) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aeropuerto(String code) {
    }
}
