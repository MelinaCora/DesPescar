package com.despescar.flightservice.mapper;

import com.despescar.flightservice.dto.airports.request.AirportRequest;
import com.despescar.flightservice.dto.airports.response.AirportResponse;
import com.despescar.flightservice.entity.Airport;

public class AirportMapper {

    private AirportMapper() {
        // Evita instanciar la clase
    }

    /**
     * Convierte un AirportRequest a una entidad Airport.
     */
    public static Airport toEntity(AirportRequest request) {
        return Airport.builder()
                .name(request.getName())
                .code(request.getCode())
                .city(request.getCity())
                .country(request.getCountry())
                .build();
    }

    /**
     * Convierte una entidad Airport a AirportResponse.
     */
    public AirportResponse toResponse(Airport airport) {
        return AirportResponse.builder()
                .id(airport.getId())
                .name(airport.getName())
                .code(airport.getCode())
                .city(airport.getCity())
                .country(airport.getCountry())
                .build();
    }

    /**
     * Actualiza una entidad existente con los datos del request.
     * Muy útil para el método PUT o PATCH.
     */
    public static void updateEntity(Airport airport, AirportRequest request) {
        airport.setName(request.getName());
        airport.setCode(request.getCode());
        airport.setCity(request.getCity());
        airport.setCountry(request.getCountry());
    }
}