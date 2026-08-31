package com.despescar.reservationservice.dto.flight.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Flight-Service devuelve campos adicionales (airline, originAirport, destinationAirport,
 * baggagePolicy, etc.) que no necesitamos aqui. Se ignoran para no romper la deserializacion.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightLookupResponse {

    private UUID id;

    private String flightNumber;

    private BigDecimal price;

    private Integer availableSeats;

    private String status;
}

