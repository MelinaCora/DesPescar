package com.despescar.flightservice.mapper;

import com.despescar.flightservice.dto.baggage.response.BaggagePolicyResponse;
import com.despescar.flightservice.entity.BaggagePolicy;

public class BaggagePolicyMapper {

    private BaggagePolicyMapper() {

    }

    public static BaggagePolicyResponse toResponse(
            BaggagePolicy baggagePolicy) {


        if (baggagePolicy == null) {
            return null;
        }


        return BaggagePolicyResponse.builder()
                .id(baggagePolicy.getId())
                .carryOnWeight(baggagePolicy.getCarryOnWeight())
                .checkedBaggageWeight(baggagePolicy.getCheckedBaggageWeight())
                .extraBaggagePrice(baggagePolicy.getExtraBaggagePrice())
                .build();
    }
}
