package com.despescar.flightservice.service;

import com.despescar.flightservice.dto.airlines.request.AirlineRequest;
import com.despescar.flightservice.dto.airlines.response.AirlineResponse;
import com.despescar.flightservice.entity.Airline;
import com.despescar.flightservice.exception.AirlineAlreadyExistsException;
import com.despescar.flightservice.exception.AirlineNotFoundException;
import com.despescar.flightservice.mapper.AirlineMapper;
import com.despescar.flightservice.repository.AirlineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AirlineService {

    private final AirlineRepository airlineRepository;
    private final AirlineMapper airlineMapper; // Inyectamos el mapper como corresponde

    public AirlineResponse createAirline(AirlineRequest request){
        if (airlineRepository.findByCode(request.getCode()).isPresent()){
            throw new AirlineAlreadyExistsException();
        }
        Airline airline = airlineMapper.toEntity(request);
        airlineRepository.save(airline);
        return airlineMapper.toResponse(airline);
    }

    public AirlineResponse getAirlineById(UUID id) {
        Airline airline = airlineRepository.findById(id).orElseThrow(AirlineNotFoundException::new);
        return airlineMapper.toResponse(airline);
    }

    public List<AirlineResponse> getAllAirlines(){
        return airlineRepository.findAll().stream()
                .map(airlineMapper::toResponse)
                .collect(Collectors.toList());
    }

    public AirlineResponse getAirlineByCode(String code){
        Airline airline = airlineRepository.findByCode(code).orElseThrow(AirlineNotFoundException::new);
        return airlineMapper.toResponse(airline);
    }

    public AirlineResponse updateAirline(UUID id, AirlineRequest request) {
        Airline airline = airlineRepository.findById(id).orElseThrow(AirlineNotFoundException::new);

        // CORREGIDO: Ahora sí existe updateEntity en el mapper y se llama mediante la instancia
        airlineMapper.updateEntity(airline, request);

        airlineRepository.save(airline);
        return airlineMapper.toResponse(airline);
    }

    public void deleteAirline(UUID id) {
        Airline airline = airlineRepository.findById(id).orElseThrow(AirlineNotFoundException::new);
        airlineRepository.delete(airline);
    }
}