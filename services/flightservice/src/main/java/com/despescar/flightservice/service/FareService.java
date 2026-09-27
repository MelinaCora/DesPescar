package com.despescar.flightservice.service;

import com.despescar.flightservice.dto.baggage.request.FareRequest;
import com.despescar.flightservice.dto.baggage.response.FareResponse;
import com.despescar.flightservice.dto.flights.response.IncludedServicesDto;
import com.despescar.flightservice.dto.flights.response.PriceDto;
import com.despescar.flightservice.entity.Fare;
import com.despescar.flightservice.repository.FareRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FareService {

    private final FareRepository fareRepository;

    /**
     * Crea una nueva tarifa.
     */
    public FareResponse create(FareRequest request) {

        Fare fare = Fare.builder()
                .name(request.getName())
                .type(request.getType())
                .personalItem(request.isPersonalItem())
                .carryOn(request.isCarryOn())
                .checkedBaggage(request.isCheckedBaggage())
                .wifi(request.isWifi())
                .seatSelection(request.getSeatSelection())
                .currency(request.getCurrency())
                .baseFare(request.getBaseFare())
                .taxesAndFees(request.getTaxesAndFees())
                .transparentFinalPrice(request.getTransparentFinalPrice())
                .build();

        Fare saved = fareRepository.save(fare);

        return mapToResponse(saved);
    }

    /**
     * Obtiene una tarifa por su ID.
     */
    public FareResponse getFareById(UUID fareId) {
        Fare fare = fareRepository.findById(fareId)
                .orElseThrow(() -> new RuntimeException("Tarifa no encontrada con ID: " + fareId));

        return mapToResponse(fare);
    }

    /**
     * Obtiene todas las tarifas existentes.
     */
    public List<FareResponse> getAllFares() {
        return fareRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Método auxiliar privado para mapear la Entidad al DTO de Respuesta.
     */
    private FareResponse mapToResponse(Fare fare) {
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
}