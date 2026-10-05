package com.despescar.flightservice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.enums.FlightStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightRepository extends JpaRepository<Flight, UUID> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    List<Flight> findByStatus(FlightStatus status);

    List<Flight> findByAirlineId(UUID airlineId);

    List<Flight> findByOriginAirportId(UUID airportId);

    List<Flight> findByDestinationAirportId(UUID airportId);

    @Query("SELECT f FROM Flight f WHERE f.originAirport.code = :origin AND f.destinationAirport.code = :destination AND f.departureTime BETWEEN :startOfDay AND :endOfDay")
    List<Flight> findFlightsForSearch(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    /** Solo los horarios de salida (sin cargar vuelos) de una ruta, con lugar, entre desde y hasta (sin incluirlo). */
    @Query("SELECT f.departureTime FROM Flight f WHERE f.originAirport.code = :origin "
            + "AND f.destinationAirport.code = :destination AND f.status = :status AND f.availableSeats > 0 "
            + "AND f.departureTime >= :desde AND f.departureTime < :hasta ORDER BY f.departureTime")
    List<LocalDateTime> findSalidasConLugar(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("status") FlightStatus status,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );
}
