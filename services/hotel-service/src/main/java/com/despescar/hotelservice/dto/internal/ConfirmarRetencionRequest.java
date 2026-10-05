package com.despescar.hotelservice.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmarRetencionRequest(@NotBlank @Size(max = 100) String nombreTitular) {
}
