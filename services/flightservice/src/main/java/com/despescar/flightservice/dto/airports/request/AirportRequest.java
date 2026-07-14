package com.despescar.flightservice.dto.airports.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AirportRequest {

    @NotBlank(message = "Airport name is required.")
    @Size(max = 100, message = "Airport name cannot exceed 100 characters.")
    private String name;

    @NotBlank(message = "Airport code is required.")
    @Pattern(
            regexp = "^[A-Z]{3}$",
            message = "Airport code must contain exactly 3 uppercase letters."
    )
    private String code;

    @NotBlank(message = "City is required.")
    @Size(max = 100, message = "City cannot exceed 100 characters.")
    private String city;

    @NotBlank(message = "Country is required.")
    @Size(max = 100, message = "Country cannot exceed 100 characters.")
    private String country;

}
