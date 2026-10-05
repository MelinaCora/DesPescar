package com.despescar.reservationservice.dto.flight.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Una tarifa de un vuelo, como la devuelve flight-service dentro de GET /api/flights/{id} (C2). */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareLookupResponse {

    private UUID id;
    private String name;
    private String type;
    private PriceDto price;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceDto {
        private String currency;
        private BigDecimal transparentFinalPrice;
    }
}
