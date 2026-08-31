package com.despescar.hotelservice.dto.hotel.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor

public class HotelRequest {
    @NotBlank(message = "El nombre del hotel es obligatorio")
    private String nombre;

    @NotBlank(message = "La ciudad es obligatoria")
    private String ciudad;

    @NotBlank(message = "La dirección es obligatoria")
    private String direccion;

    @Min(value = 1, message = "El hotel debe tener al menos 1 estrella")
    @Max(value = 5, message = "El máximo de estrellas es 5")
    private int estrellas;

    @DecimalMin(value = "0.0", message = "El precio por noche debe ser mayor o igual a 0")
    private double precioPorNoche;

    @Min(value = 0, message = "La cantidad de habitaciones disponibles no puede ser negativa")
    private int habitacionesDisponibles;

    private Boolean allInclusive;

    private UUID id;
}
