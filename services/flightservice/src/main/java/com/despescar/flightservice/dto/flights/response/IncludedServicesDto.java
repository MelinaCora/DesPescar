package com.despescar.flightservice.dto.flights.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IncludedServicesDto {
    private boolean personalItem;
    private boolean carryOn;
    private boolean checkedBaggage;
    private boolean wifi;
    private String seatSelection;
}
