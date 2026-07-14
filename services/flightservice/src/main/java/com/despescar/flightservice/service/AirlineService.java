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

    /**
     * Crea una nueva aerolínea.
     */
    public AirlineResponse createAirline(AirlineRequest request) {

        if (airlineRepository.findByCode(request.getCode()).isPresent()) {
            throw new AirlineAlreadyExistsException();
        }

        Airline airline = AirlineMapper.toEntity(request);

        airlineRepository.save(airline);

        return AirlineMapper.toResponse(airline);
    }

    /**
     * Obtiene una aerolínea por su ID.
     */
    public AirlineResponse getAirlineById(UUID id) {

        Airline airline = airlineRepository.findById(id)
                .orElseThrow(AirlineNotFoundException::new);

        return AirlineMapper.toResponse(airline);
    }

    /**
     * Obtiene todas las aerolíneas.
     */
    public List<AirlineResponse> getAllAirlines() {

        return airlineRepository.findAll()
                .stream()
                .map(AirlineMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene una aerolínea por su código.
     */
    public AirlineResponse getAirlineByCode(String code) {

        Airline airline = airlineRepository.findByCode(code)
                .orElseThrow(AirlineNotFoundException::new);

        return AirlineMapper.toResponse(airline);
    }

    /**
     * Actualiza una aerolínea.
     */
    public AirlineResponse updateAirline(UUID id, AirlineRequest request) {

        Airline airline = airlineRepository.findById(id)
                .orElseThrow(AirlineNotFoundException::new);

        airlineRepository.findByCode(request.getCode())
                .filter(a -> !a.getId().equals(id))
                .ifPresent(a -> {
                    throw new AirlineAlreadyExistsException();
                });

        AirlineMapper.updateEntity(airline, request);

        airlineRepository.save(airline);

        return AirlineMapper.toResponse(airline);
    }

    /**
     * Elimina una aerolínea.
     */
    public void deleteAirline(UUID id) {

        Airline airline = airlineRepository.findById(id)
                .orElseThrow(AirlineNotFoundException::new);

        airlineRepository.delete(airline);
    }
}