package com.despescar.flightservice.dto.flights.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScaleDto {
    private String iata;
    private String city;
    private int waitDurationMinutes;
}
