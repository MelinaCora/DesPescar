package com.despescar.reservationservice.entity;

import com.despescar.reservationservice.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "reservation_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "name_passenger")
    private String passengerName;

    @Column(name = "passenger_id")
    private String passengerDni;

    @Column(name = "fare_id")
    private UUID fareId;

    @Column(name = "fare_name")
    private String fareName;

    @Column(name = "price_charged", precision = 10, scale = 2)
    private BigDecimal priceCharged;

    @Column(name = "payer_id")
    private Long payerUserId;

    @Column(name = "payer_email")
    private String payerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus; // PENDIENTE, PAGADO

    @Column(name = "outbound_seat", length = 10)
    private String outboundSeatNumber;

    @Column(name = "return_seat", length = 10)
    private String returnSeatNumber;
}