package com.despescar.flightservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.enums.FlightStatus;

public interface FlightRepository extends JpaRepository<Flight, UUID> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    List<Flight> findByStatus(FlightStatus status);

    List<Flight> findByAirlineId(UUID airlineId);

    List<Flight> findByOriginAirportId(UUID airportId);

    List<Flight> findByDestinationAirportId(UUID airportId);
}