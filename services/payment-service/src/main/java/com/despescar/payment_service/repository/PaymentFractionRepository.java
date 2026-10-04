package com.despescar.payment_service.repository;

import com.despescar.payment_service.entity.PaymentFraction;
import com.despescar.payment_service.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentFractionRepository extends JpaRepository<PaymentFraction, UUID> {

    List<PaymentFraction> findByUserId(Long userId);

    // Crucial para los Webhooks: buscar qué fracción pertenece a un pago de MP
    Optional<PaymentFraction> findByMpPaymentId(String mpPaymentId);
}