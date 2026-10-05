package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByEstado(ReservationStatus estado);

    /** Último carrito del usuario en alguno de esos estados (el carrito activo, si existe). */
    Optional<Reservation> findFirstByCreadorIdAndEstadoInOrderByIdDesc(Long creadorId,
                                                                       Collection<ReservationStatus> estados);

    /** Carritos abiertos cuyo tiempo límite ya pasó (los cierra el scheduler). */
    List<Reservation> findByEstadoInAndLimiteTiempoBefore(Collection<ReservationStatus> estados,
                                                          LocalDateTime limite);
}
