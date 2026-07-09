package com.despescar.flightservice.dto.flights.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.despescar.flightservice.enums.FlightStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlightResponse {

    private UUID id;

    private String flightNumber;

    private String airline;

    private String originAirport;

    private String destinationAirport;

    private LocalDateTime departureTime;

    private LocalDateTime arrivalTime;

    private BigDecimal price;

    private Integer availableSeats;

    private FlightStatus status;

}
