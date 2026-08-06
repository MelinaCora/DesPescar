package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.ExtraBaggage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtraBaggageRepository extends JpaRepository<ExtraBaggage, Long> {

}