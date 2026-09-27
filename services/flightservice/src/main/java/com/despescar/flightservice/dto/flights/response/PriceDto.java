package com.despescar.flightservice.dto.flights.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;


@Data
@Builder
public class PriceDto {
    private String currency;
    private BigDecimal baseFare;
    private BigDecimal taxesAndFees;
    private BigDecimal transparentFinalPrice;
}
