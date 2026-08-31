package com.despescar.flightservice.dto.flights.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.despescar.flightservice.dto.airlines.response.AirlineResponse;
import com.despescar.flightservice.dto.airports.response.AirportResponse;
import com.despescar.flightservice.dto.baggage.response.BaggagePolicyResponse;
import com.despescar.flightservice.enums.FlightStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlightResponse {

    private UUID id;

    private String flightNumber;

    private AirlineResponse airline;

    private AirportResponse originAirport;

    private AirportResponse destinationAirport;

    private LocalDateTime departureTime;

    private LocalDateTime arrivalTime;

    private BigDecimal price;

    private Integer availableSeats;

    private FlightStatus status;

    private BaggagePolicyResponse baggagePolicy;

}
