package com.despescar.reservationservice.dto.flight.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareLookupResponse {

    private UUID id;
    private PriceDto price;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceDto {
        private String currency;
        private BigDecimal transparentFinalPrice;
    }
}
