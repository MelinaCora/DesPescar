package com.despescar.flightservice.dto.flights.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItineraryDto {
    private FlightLegDto departure;
    private FlightLegDto arrival;
    private int durationMinutes;
    private String flightType;
}
