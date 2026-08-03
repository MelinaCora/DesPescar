package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.ReservaDetalleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookingDetailRepository extends JpaRepository<ReservaDetalleEntity, Long>{

    List<ReservaDetalleEntity> findByReservaIdAndPagadorIdAndEstadoPago(Long reservaId, Long pagadorId, String estadoPago);

    long countByReservaIdAndEstadoPago(Long reservaId, String estadoPago);

    ReservaDetalleEntity findByReservaIdAndUsuarioId(Long reservaId, long usuarioId);

}
