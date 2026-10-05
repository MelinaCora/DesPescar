package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.enums.EstadoGrupo;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrupoPagoRepository extends JpaRepository<GrupoPago, Long> {

    Optional<GrupoPago> findByReservation_Id(Long reservaId);

    Optional<GrupoPago> findByTokenEnlace(String tokenEnlace);

    /** El grupo con su fila bloqueada hasta el fin de la transacción (siempre antes que la reserva). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrupoPago g where g.id = :id")
    Optional<GrupoPago> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrupoPago g where g.reservation.id = :reservaId")
    Optional<GrupoPago> findByReservaIdForUpdate(@Param("reservaId") Long reservaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrupoPago g where g.tokenEnlace = :token")
    Optional<GrupoPago> findByTokenForUpdate(@Param("token") String token);

    /** Grupos en ese estado cuyo plazo ya pasó (los cierra el scheduler, D-b14). */
    @Query("select g.id from GrupoPago g where g.estado = :estado and g.venceEn <= :ahora order by g.id")
    List<Long> idsVencidos(@Param("estado") EstadoGrupo estado, @Param("ahora") LocalDateTime ahora);

    /** Grupos en ese estado que no cambian desde antes de `limite` (reintento de confirmación, D-b15). */
    @Query("select g.id from GrupoPago g where g.estado = :estado and g.actualizadoEn <= :limite order by g.id")
    List<Long> idsSinCambiosDesde(@Param("estado") EstadoGrupo estado, @Param("limite") LocalDateTime limite);

    @Query("select g.id from GrupoPago g where g.reembolsosPendientes = true order by g.id")
    List<Long> idsConReembolsosPendientes();

    /** Grupos en esos estados donde el usuario tiene una parte (incluye los que organiza). */
    @Query("""
            select g from GrupoPago g
            where g.estado in :estados
              and exists (select p.id from ParteGrupo p where p.grupo = g and p.usuarioId = :usuarioId)
            order by g.venceEn asc, g.id asc
            """)
    List<GrupoPago> gruposConParteDe(@Param("usuarioId") Long usuarioId,
                                     @Param("estados") Collection<EstadoGrupo> estados);
}
