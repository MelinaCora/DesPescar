package com.despescar.flightservice.service;

import com.despescar.flightservice.dto.airports.request.AirportRequest;
import com.despescar.flightservice.dto.airports.response.AirportResponse;
import com.despescar.flightservice.mapper.AirportMapper;
import com.despescar.flightservice.repository.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.despescar.flightservice.entity.Airport;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AirportService {

    private final AirportRepository airportRepository;

    public AirportResponse createAirport(AirportRequest request) {

        if (airportRepository.findByCode(request.getCode()).isPresent()) {
            throw new RuntimeException("Airport code already exists.");
        }

        Airport airport = AirportMapper.toEntity(request);

        airportRepository.save(airport);

        return AirportMapper.toResponse(airport);
    }

    public AirportResponse getAirportById(UUID id) {

        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Airport not found."));

        return AirportMapper.toResponse(airport);
    }

    public List<AirportResponse> getAllAirports() {

        return airportRepository.findAll()
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<AirportResponse> getAirportsByCountry(String country) {

        return airportRepository.findByCountry(country)
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<AirportResponse> getAirportsByCity(String city) {

        return airportRepository.findByCity(city)
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }

    public AirportResponse getAirportByCode(String code) {

        Airport airport = airportRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Airport not found."));

        return AirportMapper.toResponse(airport);
    }

    public AirportResponse updateAirport(UUID id, AirportRequest request) {

        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Airport not found."));

        AirportMapper.updateEntity(airport, request);

        airportRepository.save(airport);

        return AirportMapper.toResponse(airport);
    }

    public void deleteAirport(UUID id) {

        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Airport not found."));

        airportRepository.delete(airport);
    }
}

