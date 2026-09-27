package com.despescar.flightservice.dto.flights.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AirlineSearchDto {
    private String name;
    private String logoUrl;
}
