package com.despescar.reservationservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "seats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"flight_id", "number_seat"})
})
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID seatUuid;

    @Column(nullable = false)
    private UUID flightId;

    @Column(nullable = false)
    private String numberSeat;

    @Column(nullable = false)
    private String statusSeat;

    @Column(name = "blocked_by_user_id")
    private Long blockedByUserId;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    /**
     * Carrito o reserva a la que pertenece el asiento: se fija al entrar el asiento al carrito y al
     * pagarse, y se borra al soltarlo. Distingue asientos del mismo usuario en carritos distintos: una
     * reserva solo ocupa o libera los suyos. Null en un bloqueo hecho desde el mapa, sin carrito.
     */
    @Column(name = "reserva_id")
    private Long reservaId;

}