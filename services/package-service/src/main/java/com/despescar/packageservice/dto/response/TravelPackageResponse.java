package com.despescar.packageservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
