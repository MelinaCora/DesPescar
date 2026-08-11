package com.despescar.reservationservice.dto.flight.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
public class FlightLookupResponse {

    private UUID id;

    private String flightNumber;

    private BigDecimal price;

    private Integer availableSeats;

    private String status;
}

