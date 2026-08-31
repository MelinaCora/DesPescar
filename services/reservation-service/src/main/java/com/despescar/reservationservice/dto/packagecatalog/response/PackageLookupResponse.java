package com.despescar.reservationservice.dto.packagecatalog.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Package-Service devuelve tambien createdAt/updatedAt que no necesitamos aqui.
 * Se ignoran para no romper la deserializacion.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
