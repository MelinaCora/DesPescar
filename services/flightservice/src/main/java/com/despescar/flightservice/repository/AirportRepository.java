package com.despescar.flightservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.despescar.flightservice.entity.Airport;

public interface AirportRepository extends JpaRepository<Airport, UUID> {

    Optional<Airport> findByCode(String code);

    List<Airport> findByCountry(String country);

    List<Airport> findByCity(String city);

}