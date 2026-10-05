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
        @NotNull @Min(1) @Max(10) Integer capacidad,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal precioPorNoche,
        @NotNull @Min(1) @Max(500) Integer cantidadUnidades,
        @NotNull List<@NotNull @Pattern(regexp = "^https://\\S+$", message = "Las imágenes tienen que ser URLs https") String> imagenes) {
}
