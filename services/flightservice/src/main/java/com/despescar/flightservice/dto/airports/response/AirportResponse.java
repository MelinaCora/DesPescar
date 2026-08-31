package com.despescar.flightservice.dto.airports.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class AirportResponse {

    private UUID id;

    private String name;

    private String code;

    private String city;

    private String country;


}
