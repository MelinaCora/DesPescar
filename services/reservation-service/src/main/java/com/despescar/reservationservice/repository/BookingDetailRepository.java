package com.despescar.reservationservice.repository;

import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookingDetailRepository extends JpaRepository<ReservationDetail, Long> {

    // 1. reservation, payerUserId, paymentStatus
    List<ReservationDetail> findByReservation_IdAndPayerUserIdAndPaymentStatus(
            Long reservationId,
            Long payerUserId,
            PaymentStatus paymentStatus
    );

    // 2. reservation
    List<ReservationDetail> findByReservation_Id(Long reservationId);

    // 3. reservation, paymentStatus
    long countByReservation_IdAndPaymentStatus(Long reservationId, PaymentStatus paymentStatus);

    // 4. reservation, payerUserId
    ReservationDetail findByReservation_IdAndPayerUserId(Long reservationId, Long payerUserId);

}