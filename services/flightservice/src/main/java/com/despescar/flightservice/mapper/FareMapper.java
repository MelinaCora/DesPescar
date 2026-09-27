package com.despescar.flightservice.mapper;

import com.despescar.flightservice.dto.baggage.response.FareResponse;
import com.despescar.flightservice.dto.flights.response.IncludedServicesDto;
import com.despescar.flightservice.dto.flights.response.PriceDto;
import com.despescar.flightservice.entity.Fare;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FareMapper {

    private FareMapper() {
        // Constructor privado para evitar instanciación
    }

    public static FareResponse toResponse(Fare fare) {
        if (fare == null) {
            return null;
        }

        return FareResponse.builder()
                .id(fare.getId())
                .name(fare.getName())
                .type(fare.getType())
                .includedServices(IncludedServicesDto.builder()
                        .personalItem(fare.isPersonalItem())
                        .carryOn(fare.isCarryOn())
                        .checkedBaggage(fare.isCheckedBaggage())
                        .wifi(fare.isWifi())
                        .seatSelection(fare.getSeatSelection())
                        .build())
                .price(PriceDto.builder()
                        .currency(fare.getCurrency())
                        .baseFare(fare.getBaseFare())
                        .taxesAndFees(fare.getTaxesAndFees())
                        .transparentFinalPrice(fare.getTransparentFinalPrice())
                        .build())
                .build();
    }

    public static List<FareResponse> toResponse(List<Fare> fares) {
        if (fares == null || fares.isEmpty()) {
            return Collections.emptyList();
        }

        return fares.stream()
                .map(FareMapper::toResponse)
                .collect(Collectors.toList());
    }
}