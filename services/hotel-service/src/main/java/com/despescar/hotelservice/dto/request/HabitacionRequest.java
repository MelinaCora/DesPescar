package com.despescar.hotelservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record HabitacionRequest(
        @NotBlank @Size(max = 120) String nombre,
        @Size(max = 1000) String descripcion,
        @Min(1) @Max(10) int capacidad,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal precioPorNoche,
        @Min(1) @Max(500) int cantidadUnidades,
        @NotNull List<@Pattern(regexp = "^https://\\S+$", message = "Las imágenes tienen que ser URLs https") String> imagenes) {
}
