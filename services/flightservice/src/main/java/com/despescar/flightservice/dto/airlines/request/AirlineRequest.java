package com.despescar.flightservice.dto.airlines.request;

import lombok.Data;

@Data


public class AirlineRequest {
    private String name;

    private String code;

    private String country;

    private String logoUrl;

}
