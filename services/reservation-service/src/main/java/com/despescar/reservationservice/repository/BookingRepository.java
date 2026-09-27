package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Reservation, Long>{

    List<Reservation> findByEstado(ReservationStatus estado);

}
