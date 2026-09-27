package com.despescar.flightservice.dto.flights.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlightLegDto {
    private String iata;
    private String dateTime;
}
