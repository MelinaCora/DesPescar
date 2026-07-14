package com.despescar.flightservice.dto.airports.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AirportRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Size(min = 3, max = 3)
    private String code;

    @NotBlank
    private String city;

    @NotBlank
    private String country;

}
