package com.despescar.hotelservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "hoteles")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class Hotel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "El nombre del hotel es obligatorio")
    @Column(nullable = false)
    private String nombre;

    @NotBlank(message = "La ciudad es obligatoria")
    @Column(nullable = false)
    private String ciudad;

    @NotBlank(message = "La dirección es obligatoria")
    private String direccion;

    @Min(value = 1, message = "El hotel debe tener al menos 1 estrella")
    @Max(value = 5, message = "El máximo de estrellas es 5")
    private int estrellas;

    @DecimalMin(value = "0.0", message = "El precio por noche debe ser mayor o igual a 0")
    @Column(name = "precio_por_noche", nullable = false)
    private double precioPorNoche;

    @Min(value = 0, message = "La cantidad de habitaciones disponibles no puede ser negativa")
    @Column(name = "habitaciones_disponibles", nullable = false)
    private int habitacionesDisponibles;

    @Column(name = "all_inclusive", nullable = false)
    private Boolean allInclusive;
}
