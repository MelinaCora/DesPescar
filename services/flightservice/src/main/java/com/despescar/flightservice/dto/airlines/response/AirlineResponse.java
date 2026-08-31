package com.despescar.flightservice.dto.airlines.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class AirlineResponse {

    private UUID id;

    private String name;

    private String code;

    private String country;

    private String logoUrl;


}
