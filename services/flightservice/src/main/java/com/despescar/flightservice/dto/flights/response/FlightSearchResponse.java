package com.despescar.flightservice.dto.flights.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FlightSearchResponse {
    private MetadataDto metadata;
    private List<DetailedFlightResponseDto> departureFlights;
    private List<DetailedFlightResponseDto> returnFlights;

}
