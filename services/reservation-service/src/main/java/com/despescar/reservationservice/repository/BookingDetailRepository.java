package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.ReservationPaymentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookingDetailRepository extends JpaRepository<ReservationDetail, Long>{

    List<ReservationDetail> findByReservaIdAndPagadorIdAndEstadoPago(Long reservaId, Long pagadorId, ReservationPaymentState estadoPago);

    List<ReservationDetail> findByReservaId(Long reservaId);

    long countByReservaIdAndEstadoPago(Long reservaId, ReservationPaymentState estadoPago);

    ReservationDetail findByReservaIdAndUsuarioId(Long reservaId, long usuarioId);

}
