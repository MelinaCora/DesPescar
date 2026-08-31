package com.despescar.flightservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.despescar.flightservice.entity.BaggagePolicy;


@Repository
public interface BaggagePolicyRepository extends JpaRepository<BaggagePolicy, UUID> {

}