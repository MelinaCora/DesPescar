package com.despescar.hotelservice.repository;

import com.despescar.hotelservice.entity.Retencion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetencionRepository extends JpaRepository<Retencion, UUID> {

    /**
     * Retenciones que ocupan alguna noche de [desde, hasta): confirmadas, o retenidas que todavía
     * no vencieron. Las vencidas no cuentan aunque nadie las haya liberado.
     * No llamar con una colección vacía.
     */
    @Query("""
            select r from Retencion r
            where r.tipoHabitacionId in :tipos
              and r.checkIn < :hasta and r.checkOut > :desde
              and (r.estado = com.despescar.hotelservice.entity.EstadoRetencion.CONFIRMADA
                   or (r.estado = com.despescar.hotelservice.entity.EstadoRetencion.RETENIDA
                       and r.expiraEn > :ahora))
            """)
    List<Retencion> findActivasQueSolapan(@Param("tipos") Collection<UUID> tipos,
                                          @Param("desde") LocalDate desde,
                                          @Param("hasta") LocalDate hasta,
                                          @Param("ahora") Instant ahora);
}
