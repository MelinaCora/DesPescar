package com.despescar.reservationservice.dto.packagecatalog.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
public class PackageLookupResponse {

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
