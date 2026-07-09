package com.despescar.flightservice.dto.flights.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data

public class FlightRequest {
	//entrada de datos
    private String flightNumber;

    private UUID airlineId;

    private UUID originAirportID;

    private UUID destinationAirportID;

    private LocalDateTime departureTime;

    private LocalDateTime arrivalTime;

    private BigDecimal price;

    private Integer avaibleSeates;


}
