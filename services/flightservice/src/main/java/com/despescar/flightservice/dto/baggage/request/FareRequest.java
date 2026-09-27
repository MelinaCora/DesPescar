package com.despescar.flightservice.dto.baggage.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FareRequest {
    private String name;
    private String type;

    private boolean personalItem;
    private boolean carryOn;
    private boolean checkedBaggage;
    private boolean wifi;
    private String seatSelection;

    private String currency;
    private BigDecimal baseFare;
    private BigDecimal taxesAndFees;
    private BigDecimal transparentFinalPrice;
}