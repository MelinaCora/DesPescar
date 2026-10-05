package com.despescar.hotelservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Unidades de un tipo de habitación tomadas por una reserva para un rango de noches. */
@Entity
@Table(name = "retenciones",
        indexes = @Index(name = "idx_retencion_tipo_fechas", columnList = "tipo_habitacion_id, check_in, check_out"))
@Getter
@Setter
@NoArgsConstructor
public class Retencion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "tipo_habitacion_id", nullable = false)
    private UUID tipoHabitacionId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false)
    private int huespedes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRetencion estado;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "nombre_titular")
    private String nombreTitular;
}
