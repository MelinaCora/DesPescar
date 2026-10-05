package com.despescar.hotelservice.repository;

import com.despescar.hotelservice.entity.TipoHabitacion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TipoHabitacionRepository extends JpaRepository<TipoHabitacion, UUID> {

    /**
     * Bloquea la fila del tipo hasta el fin de la transacción: dos pedidos simultáneos por la
     * última unidad se atienden de a uno y el segundo ya ve la retención del primero.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TipoHabitacion t where t.id = :id")
    Optional<TipoHabitacion> findByIdForUpdate(@Param("id") UUID id);
}
