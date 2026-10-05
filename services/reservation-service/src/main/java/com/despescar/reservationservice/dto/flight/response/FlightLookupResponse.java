package com.despescar.reservationservice.dto.flight.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/flights/{id} de flight-service (contrato C2). Trae el precio base y las tarifas del
 * vuelo, con lo que se calcula el precio del carrito (D1). Se ignoran airline, aeropuertos, etc.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightLookupResponse {

    private UUID id;

    private String flightNumber;

    private LocalDateTime departureTime;

    private BigDecimal price;

    private Integer availableSeats;

    private String status;

    private List<FareLookupResponse> fares = new ArrayList<>();
}
