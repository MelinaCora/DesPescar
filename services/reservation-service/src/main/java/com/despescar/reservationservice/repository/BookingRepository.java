package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * La reserva con su fila bloqueada (FOR UPDATE) hasta el fin de la transacción. La usan la
     * confirmación del pago, el vencimiento y el abandono, para que ninguno pise al otro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}
