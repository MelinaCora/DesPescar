package com.despescar.flightservice.service;

import com.despescar.flightservice.dto.flights.request.FlightRequest;
import com.despescar.flightservice.dto.flights.response.FlightResponse;
import com.despescar.flightservice.entity.Airline;
import com.despescar.flightservice.entity.Airport;
import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.exception.AirportNotFoundException;
import com.despescar.flightservice.exception.AirlineNotFoundException;
import com.despescar.flightservice.exception.FlightNotFoundException;
import com.despescar.flightservice.exception.FlightNumberAlreadyExistsException;
import com.despescar.flightservice.mapper.FlightMapper;
import com.despescar.flightservice.repository.AirlineRepository;
import com.despescar.flightservice.repository.AirportRepository;
import com.despescar.flightservice.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final AirlineRepository airlineRepository;
    private final AirportRepository airportRepository;
    private final FlightMapper flightMapper; // Inyectamos el mapper del servicio

    public FlightResponse createFlight(FlightRequest request) {
        if(flightRepository.findByFlightNumber(request.getFlightNumber()).isPresent()){
            throw new FlightNumberAlreadyExistsException();
        }

        Airline airline = airlineRepository.findById(request.getAirlineId()).orElseThrow(AirlineNotFoundException::new);

        // CORREGIDO: Lanzaba AirlineNotFoundException en vez de AirportNotFoundException
        Airport originAirport = airportRepository.findById(request.getOriginAirportId()).orElseThrow(AirportNotFoundException::new);
        Airport destinationAirport = airportRepository.findById(request.getDestinationAirportId()).orElseThrow(AirportNotFoundException::new);

        // CORREGIDO: Usamos la instancia en minúscula
        Flight flight = flightMapper.toEntity(request);

        flight.setAirline(airline);
        flight.setOriginAirport(originAirport);
        flight.setDestinationAirport(destinationAirport);

        flightRepository.save(flight);

        return flightMapper.toResponse(flight);
    }

    public List<FlightResponse> getAllFlights() {
        return flightRepository.findAll().stream()
                .map(flightMapper::toResponse)
                .collect(Collectors.toList());
    }

    public FlightResponse getFlightByNumbers(String flightNumber) {
        Flight flight = flightRepository.findByFlightNumber(flightNumber).orElseThrow(FlightNotFoundException::new);
        return flightMapper.toResponse(flight);
    }

    public FlightResponse updateFlight(UUID id, FlightRequest request) {
        Flight flight = flightRepository.findById(id).orElseThrow(FlightNotFoundException::new);

        Airline airline = airlineRepository.findById(request.getAirlineId()).orElseThrow(AirlineNotFoundException::new);

        // CORREGIDO: Lanzaba AirlineNotFoundException en vez de AirportNotFoundException
        Airport originAirport = airportRepository.findById(request.getOriginAirportId()).orElseThrow(AirportNotFoundException::new);
        Airport destinationAirport = airportRepository.findById(request.getDestinationAirportId()).orElseThrow(AirportNotFoundException::new);

        // CORREGIDO: Ahora sí existe en el mapper y se llama mediante la instancia
        flightMapper.updateEntity(flight, request);

        flight.setAirline(airline);
        flight.setOriginAirport(originAirport);
        flight.setDestinationAirport(destinationAirport);

        flightRepository.save(flight);
        return flightMapper.toResponse(flight);
    }

    public void deleteFlight(UUID id) {
        Flight flight = flightRepository.findById(id).orElseThrow(FlightNotFoundException::new);
        flightRepository.delete(flight);
    }

    public List<FlightResponse> getFlightsByAirline(UUID airlineId) {
        return flightRepository.findByAirlineId(airlineId).stream()
                .map(flightMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<FlightResponse> getFlightsByOrigin(UUID airportId){
        return flightRepository.findByDestinationAirportId(airportId).stream()
                .map(flightMapper::toResponse)
                .collect(Collectors.toList());
    }
}