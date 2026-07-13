package com.despescar.flightservice.mapper;

import com.despescar.flightservice.dto.airlines.request.AirlineRequest;
import com.despescar.flightservice.dto.airlines.response.AirlineResponse;
import com.despescar.flightservice.entity.Airline;
import org.springframework.stereotype.Component;

@Component // Habilita a Spring para que pueda inyectar este mapper en los servicios
public class AirlineMapper {

    public Airline toEntity(AirlineRequest request) {
        if (request == null) {
            return null;
        }
        return Airline.builder()
                .name(request.getName())
                .code(request.getCode())
                .country(request.getCountry())
                .logoUrl(request.getLogoUrl())
                .build();
    }

    public AirlineResponse toResponse(Airline airline){
        if (airline == null) {
            return null;
        }
        return AirlineResponse.builder()
                .id(airline.getId())
                .name(airline.getName())
                .code(airline.getCode())
                .country(airline.getCountry())
                .logoUrl(airline.getLogoUrl())
                .build();
    }

    // ¡ESTE ERA EL MÉTODO QUE TE FALTABA CREAR!
    public void updateEntity(Airline airline, AirlineRequest request) {
        if (airline == null || request == null) {
            return;
        }
        // Actualizamos los datos de la entidad existente con lo que viene del request
        airline.setName(request.getName());
        airline.setCode(request.getCode());
        airline.setCountry(request.getCountry());
        airline.setLogoUrl(request.getLogoUrl());
    }
}


