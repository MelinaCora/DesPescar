package com.despescar.payment_service.entity;

import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long userId;

    private UUID passengerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column
    private PaymentMethod paymentMethod;

    @Column(unique = true)
    private String transactionId;

    private String preferenceId;

    private String checkoutUrl;

    private LocalDateTime paymentDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    /**
     * Igual a reservationId mientras el pago esta PENDING y NULL en cualquier otro estado. Su indice
     * unico impide dos pagos PENDING de la misma reserva aunque lleguen dos pedidos a la vez.
     */
    @Column(unique = true)
    private Long pendienteDeReserva;

    @OneToMany(
            mappedBy = "payment",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PaymentHistory> history = new ArrayList<>();

    @PreUpdate
    void preUpdate() {
        sincronizarPendiente();
    }

    private void sincronizarPendiente() {
        pendienteDeReserva = status == PaymentStatus.PENDING ? reservationId : null;
    }

    @PrePersist
    void prePersist() {
        sincronizarPendiente();
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (provider == null) {
            provider = PaymentProvider.MERCADO_PAGO;
        }
    }

}