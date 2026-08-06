package com.despescar.flightservice.dto.baggage.request;
import java.math.BigDecimal;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaggagePolicyRequest {
    private Integer carryOnWeight;

    private Integer checkedBaggageWeight;

    private BigDecimal extraBaggagePrice;
}
