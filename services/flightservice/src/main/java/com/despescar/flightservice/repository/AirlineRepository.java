package com.despescar.flightservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.despescar.flightservice.entity.Airline;

public interface AirlineRepository extends JpaRepository<Airline, UUID> {

    Optional<Airline> findByCode(String code);

    Optional<Airline> findByName(String name);

}