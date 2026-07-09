package com.despescar.flightservice.mapper;

import org.springframework.sterotype.Component;
import com.despescar.flightservce.dto..airlines.request.AirlineRequest;
import com.despescar.fligthservice.dto.airlines.response.AirlineResponse;
import com.despescar.flightservice.entity.Airline;

@Component
public class AirlineMapper {
    public Airline toEntity(AirlineRequest request) {
        return Airline.builder()
                .name(request.getName())
                .code(request.getCode())
                .country(request.getCountry())
                .logoUrl(request.getLogoUrl())
                .build()
    }
    public AirlineResponse toResponse(Airline airline){
        return AirlineResponse.builder()
                .id(airline.getId())
                .name(airline.getName())
                .code(airline.getCode())
                .country(airline.getCountry())
                .logoUrl(airline.getLogoUrl())
                .build()
    }

}



