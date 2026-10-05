package com.despescar.payment_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByReservationId(Long reservationId);

    List<Payment> findByUserId(Long userId);

    List<Payment> findByStatus(PaymentStatus status);

    Optional<Payment> findByTransactionId(String transactionId);

    /** Lee el pago bloqueando la fila: webhook, conciliacion y simulacion no lo procesan a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdParaActualizar(@Param("id") UUID id);

    /** Pagos de partes de un grupo, en orden fijo de id (se bloquean de a uno en ese orden). */
    @Query("select p.id from Payment p where p.reservationId = :reservationId and p.parteNumero is not null order by p.id")
    List<UUID> idsDePartes(@Param("reservationId") Long reservationId);

}
