package com.despescar.flightservice.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.despescar.flightservice.entity.Fare;

public interface FareRepository extends JpaRepository<Fare, UUID> {

}