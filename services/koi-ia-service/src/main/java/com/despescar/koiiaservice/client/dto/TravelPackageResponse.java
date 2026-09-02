package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TravelPackageResponse {
    private Long id;
    private String name;
    private String description;
    private String destination;
    private String flightNumber;
    private UUID hotelId;
    private Integer durationNights;
    private BigDecimal basePrice;
    private boolean active;
}
