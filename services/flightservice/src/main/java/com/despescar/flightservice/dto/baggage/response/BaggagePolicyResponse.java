package com.despescar.flightservice.dto.baggage.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaggagePolicyResponse {
    private UUID id;

    private Integer carryOnWeight;

    private Integer checkedBaggageWeight;

    private BigDecimal extraBaggagePrice;
}
