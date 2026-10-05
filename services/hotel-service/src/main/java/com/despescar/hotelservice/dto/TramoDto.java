package com.despescar.hotelservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TramoDto(@Min(0) int horasAntes, @Min(0) @Max(100) int porcentajeReembolso) {
}
