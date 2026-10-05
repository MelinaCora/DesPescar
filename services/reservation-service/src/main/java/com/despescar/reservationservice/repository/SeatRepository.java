package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByFlightId(UUID flightId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.seatUuid = :seatUuid")
    Optional<Seat> findByIdForUpdate(@Param("seatUuid") UUID seatUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.flightId = :flightId AND s.numberSeat = :numberSeat")
    Optional<Seat> findByFlightIdAndNumberSeatForUpdate(@Param("flightId") UUID flightId,
                                                        @Param("numberSeat") String numberSeat);

    List<Seat> findByStatusSeatAndBloqueadoHastaBefore(String statusSeat, LocalDateTime time);

    /** Solo los ids: cada asiento se relee bloqueado (FOR UPDATE) antes de soltarlo. */
    @Query("SELECT s.seatUuid FROM Seat s WHERE s.statusSeat = :statusSeat AND s.bloqueadoHasta < :limite")
    List<UUID> findIdsByStatusSeatAndBloqueadoHastaBefore(@Param("statusSeat") String statusSeat,
                                                         @Param("limite") LocalDateTime limite);

    /** Ids de los asientos de un vuelo atados a un carrito; cada uno se relee bloqueado antes de tocarlo. */
    @Query("SELECT s.seatUuid FROM Seat s WHERE s.flightId = :flightId AND s.reservaId = :reservaId ORDER BY s.numberSeat")
    List<UUID> findIdsByFlightIdAndReservaId(@Param("flightId") UUID flightId, @Param("reservaId") Long reservaId);
}
