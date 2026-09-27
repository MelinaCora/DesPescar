package com.despescar.flightservice.dto.flights.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.despescar.flightservice.dto.baggage.request.FareRequest;
import com.despescar.flightservice.enums.FlightStatus;
import lombok.Data;

@Data

public class FlightRequest {
    private String flightNumber;
    private UUID airlineId;
    private UUID originAirportId;
    private UUID destinationAirportId;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private BigDecimal price;
    private Integer availableSeats;
    private FlightStatus status;
    private List<UUID> faresId;

}
