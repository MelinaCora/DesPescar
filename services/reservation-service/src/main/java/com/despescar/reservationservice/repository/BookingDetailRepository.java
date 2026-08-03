package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.ReservaDetalleEntity;
import com.despescar.reservationservice.enums.EstadoPagoReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookingDetailRepository extends JpaRepository<ReservaDetalleEntity, Long>{

    List<ReservaDetalleEntity> findByReservaIdAndPagadorIdAndEstadoPago(Long reservaId, Long pagadorId, EstadoPagoReserva estadoPago);

    List<ReservaDetalleEntity> findByReservaId(Long reservaId);

    long countByReservaIdAndEstadoPago(Long reservaId, EstadoPagoReserva estadoPago);

    ReservaDetalleEntity findByReservaIdAndUsuarioId(Long reservaId, long usuarioId);

}
