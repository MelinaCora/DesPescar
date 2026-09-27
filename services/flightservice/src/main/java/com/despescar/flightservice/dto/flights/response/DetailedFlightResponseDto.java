package com.despescar.flightservice.dto.flights.response;

import com.despescar.flightservice.dto.baggage.response.FareResponse;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DetailedFlightResponseDto {
    private UUID id;
    private String flightNumber;
    private AirlineSearchDto airline;
    private String aircraft;
    private ItineraryDto itinerary;
    private List<ScaleDto> scales;
    private IncludedServicesDto includedServices;
    private PriceDto price;
    private List<FareResponse> fares;
}
