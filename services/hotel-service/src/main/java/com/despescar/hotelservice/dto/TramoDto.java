package com.despescar.hotelservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TramoDto(@NotNull @Min(0) Integer horasAntes, @NotNull @Min(0) @Max(100) Integer porcentajeReembolso) {
}
