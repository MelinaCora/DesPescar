package com.despescar.flightservice.mapper;

import com.despescar.flightservice.dto.airlines.request.AirlineRequest;
import com.despescar.flightservice.dto.airlines.response.AirlineResponse;
import com.despescar.flightservice.entity.Airline;


public class AirlineMapper {
    public static Airline toEntity(AirlineRequest request) {
        return Airline.builder()
                .name(request.getName())
                .code(request.getCode())
                .country(request.getCountry())
                .logoUrl(request.getLogoUrl())
                .build();
    }
    public static AirlineResponse toResponse(Airline airline){
        return AirlineResponse.builder()
                .id(airline.getId())
                .name(airline.getName())
                .code(airline.getCode())
                .country(airline.getCountry())
                .logoUrl(airline.getLogoUrl())
                .build();
    }

}



