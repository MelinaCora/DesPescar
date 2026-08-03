package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.ReservaEntity;
import com.despescar.reservationservice.enums.EstadoReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<ReservaEntity, Long>{

    List<ReservaEntity> findByEstado(EstadoReserva estado);

}
