package com.despescar.flightservice.service;

import com.despescar.flightservice.dto.flights.request.FlightRequest;
import com.despescar.flightservice.dto.flights.response.FlightResponse;
import com.despescar.flightservice.entity.Airline;
import com.despescar.flightservice.entity.Airport;
import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.exception.AirlineNotFoundException;
import com.despescar.flightservice.exception.AirportNotFoundException;
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

    /**
     * Crea un nuevo vuelo.
     */
    public FlightResponse createFlight(FlightRequest request) {

        if (flightRepository.findByFlightNumber(request.getFlightNumber()).isPresent()) {
            throw new FlightNumberAlreadyExistsException();
        }

        Airline airline = airlineRepository.findById(request.getAirlineId())
                .orElseThrow(AirlineNotFoundException::new);

        Airport originAirport = airportRepository.findById(request.getOriginAirportId())
                .orElseThrow(AirportNotFoundException::new);

        Airport destinationAirport = airportRepository.findById(request.getDestinationAirportId())
                .orElseThrow(AirportNotFoundException::new);

        Flight flight = FlightMapper.toEntity(request);

        flight.setAirline(airline);
        flight.setOriginAirport(originAirport);
        flight.setDestinationAirport(destinationAirport);

        flightRepository.save(flight);

        return FlightMapper.toResponse(flight);
    }

    /**
     * Obtiene todos los vuelos.
     */
    public List<FlightResponse> getAllFlights() {

        return flightRepository.findAll()
                .stream()
                .map(FlightMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca un vuelo por ID.
     */
    public FlightResponse getFlightById(UUID id) {

        Flight flight = flightRepository.findById(id)
                .orElseThrow(FlightNotFoundException::new);

        return FlightMapper.toResponse(flight);
    }

    /**
     * Busca un vuelo por número.
     */
    public FlightResponse getFlightByNumber(String flightNumber) {

        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(FlightNotFoundException::new);

        return FlightMapper.toResponse(flight);
    }

    /**
     * Actualiza un vuelo existente.
     */
    public FlightResponse updateFlight(UUID id, FlightRequest request) {

        Flight flight = flightRepository.findById(id)
                .orElseThrow(FlightNotFoundException::new);

        Airline airline = airlineRepository.findById(request.getAirlineId())
                .orElseThrow(AirlineNotFoundException::new);

        Airport originAirport = airportRepository.findById(request.getOriginAirportId())
                .orElseThrow(AirportNotFoundException::new);

        Airport destinationAirport = airportRepository.findById(request.getDestinationAirportId())
                .orElseThrow(AirportNotFoundException::new);

        FlightMapper.updateEntity(flight, request);

        flight.setAirline(airline);
        flight.setOriginAirport(originAirport);
        flight.setDestinationAirport(destinationAirport);

        flightRepository.save(flight);

        return FlightMapper.toResponse(flight);
    }

    /**
     * Elimina un vuelo.
     */
    public void deleteFlight(UUID id) {

        Flight flight = flightRepository.findById(id)
                .orElseThrow(FlightNotFoundException::new);

        flightRepository.delete(flight);
    }

    /**
     * Obtiene todos los vuelos de una aerolínea.
     */
    public List<FlightResponse> getFlightsByAirline(UUID airlineId) {

        return flightRepository.findByAirlineId(airlineId)
                .stream()
                .map(FlightMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los vuelos cuyo origen es un aeropuerto.
     */
    public List<FlightResponse> getFlightsByOrigin(UUID airportId) {

        return flightRepository.findByOriginAirportId(airportId)
                .stream()
                .map(FlightMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los vuelos cuyo destino es un aeropuerto.
     */
    public List<FlightResponse> getFlightsByDestination(UUID airportId) {

        return flightRepository.findByDestinationAirportId(airportId)
                .stream()
                .map(FlightMapper::toResponse)
                .collect(Collectors.toList());
    }
}