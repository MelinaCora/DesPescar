package com.despescar.reservationservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;


@Entity
@Table(name = "equipaje_extra")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtraBaggage {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "detalle_reserva_id",
            nullable = false
    )

    @ToString.Exclude
    private ReservationDetail detalleReserva;


    @Column(name = "peso_kg", nullable = false)
    private Double peso;

    @Column(name = "precio_extra", nullable = false)
    private BigDecimal precio;

}