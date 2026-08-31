package com.despescar.flightservice.service;

import com.despescar.flightservice.dto.baggage.request.BaggagePolicyRequest;
import com.despescar.flightservice.dto.baggage.response.BaggagePolicyResponse;
import com.despescar.flightservice.entity.BaggagePolicy;
import com.despescar.flightservice.repository.BaggagePolicyRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BaggagePolicyService {
    private final BaggagePolicyRepository baggagePolicyRepository;


    public BaggagePolicyResponse create(
            BaggagePolicyRequest request) {


        BaggagePolicy baggagePolicy = BaggagePolicy.builder()
                .carryOnWeight(request.getCarryOnWeight())
                .checkedBaggageWeight(request.getCheckedBaggageWeight())
                .extraBaggagePrice(request.getExtraBaggagePrice())
                .build();


        BaggagePolicy saved = baggagePolicyRepository.save(baggagePolicy);


        return mapToResponse(saved);
    }



    public List<BaggagePolicyResponse> findAll() {


        return baggagePolicyRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();

    }



    public BaggagePolicyResponse findById(UUID id) {


        BaggagePolicy baggagePolicy =
                baggagePolicyRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Baggage policy not found"));


        return mapToResponse(baggagePolicy);
    }



    private BaggagePolicyResponse mapToResponse(
            BaggagePolicy baggagePolicy) {


        return BaggagePolicyResponse.builder()
                .id(baggagePolicy.getId())
                .carryOnWeight(baggagePolicy.getCarryOnWeight())
                .checkedBaggageWeight(baggagePolicy.getCheckedBaggageWeight())
                .extraBaggagePrice(baggagePolicy.getExtraBaggagePrice())
                .build();

    }
}
