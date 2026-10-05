package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Lo que KOI usa de GET /api/flights/search (flightservice, público). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusquedaVuelosResponse(List<VueloBuscado> departureFlights, List<VueloBuscado> returnFlights) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VueloBuscado(UUID id, String flightNumber, Aerolinea airline, Itinerario itinerary,
                               List<Tarifa> fares) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aerolinea(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Itinerario(Tramo departure, Tramo arrival) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tramo(String iata, String dateTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tarifa(UUID id, String name, Precio price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Precio(String currency, BigDecimal transparentFinalPrice) {
    }
}
