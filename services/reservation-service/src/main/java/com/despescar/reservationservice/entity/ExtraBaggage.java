package com.despescar.reservationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "equipaje_extra")
@Data
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
    private ReservationDetail detalleReserva;


    @Column(nullable = false)
    private Double peso;


    @Column(nullable = false)
    private Double precio;

}