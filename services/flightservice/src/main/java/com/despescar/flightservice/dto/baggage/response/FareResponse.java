package com.despescar.flightservice.dto.baggage.response;

import com.despescar.flightservice.dto.flights.response.IncludedServicesDto;
import com.despescar.flightservice.dto.flights.response.PriceDto;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareResponse {
    private UUID id;
    private String name;
    private String type;
    private IncludedServicesDto includedServices;
    private PriceDto price;

}
