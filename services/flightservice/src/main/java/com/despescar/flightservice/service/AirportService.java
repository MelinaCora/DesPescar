package com.despescar.flightservice.service;

import com.despescar.flightservice.dto.airports.request.AirportRequest;
import com.despescar.flightservice.dto.airports.response.AirportResponse;
import com.despescar.flightservice.entity.Airport;
import com.despescar.flightservice.exception.AirportCodeAlreadyExistException;
import com.despescar.flightservice.exception.AirportNotFoundException;
import com.despescar.flightservice.mapper.AirportMapper;
import com.despescar.flightservice.repository.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AirportService {

    private final AirportRepository airportRepository;

    /**
     * Crea un nuevo aeropuerto.
     */
    public AirportResponse createAirport(AirportRequest request) {

        if (airportRepository.findByCode(request.getCode()).isPresent()) {
            throw new AirportCodeAlreadyExistException();
        }

        Airport airport = AirportMapper.toEntity(request);

        airportRepository.save(airport);

        return AirportMapper.toResponse(airport);
    }

    /**
     * Obtiene un aeropuerto por su ID.
     */
    public AirportResponse getAirportById(UUID id) {

        Airport airport = airportRepository.findById(id)
                .orElseThrow(AirportNotFoundException::new);

        return AirportMapper.toResponse(airport);
    }

    /**
     * Obtiene todos los aeropuertos.
     */
    public List<AirportResponse> getAllAirports() {

        return airportRepository.findAll()
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene los aeropuertos de un país.
     */
    public List<AirportResponse> getAirportsByCountry(String country) {

        return airportRepository.findByCountry(country)
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene los aeropuertos de una ciudad.
     */
    public List<AirportResponse> getAirportsByCity(String city) {

        return airportRepository.findByCity(city)
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene un aeropuerto por su código IATA.
     */
    public AirportResponse getAirportByCode(String code) {

        Airport airport = airportRepository.findByCode(code)
                .orElseThrow(AirportNotFoundException::new);

        return AirportMapper.toResponse(airport);
    }

    /**
     * Actualiza un aeropuerto.
     */
    public AirportResponse updateAirport(UUID id, AirportRequest request) {

        Airport airport = airportRepository.findById(id)
                .orElseThrow(AirportNotFoundException::new);

        airportRepository.findByCode(request.getCode())
                .filter(a -> !a.getId().equals(id))
                .ifPresent(a -> {
                    throw new AirportCodeAlreadyExistException();
                });

        AirportMapper.updateEntity(airport, request);

        airportRepository.save(airport);

        return AirportMapper.toResponse(airport);
    }

    /**
     * Elimina un aeropuerto.
     */
    public void deleteAirport(UUID id) {

        Airport airport = airportRepository.findById(id)
                .orElseThrow(AirportNotFoundException::new);

        airportRepository.delete(airport);
    }
}