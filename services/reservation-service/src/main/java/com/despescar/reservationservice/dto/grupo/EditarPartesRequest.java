package com.despescar.reservationservice.dto.grupo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Uno de los dos: cantidadPartes (vuelve a partes iguales) o montos (uno por parte, en orden). */
public record EditarPartesRequest(
        @Min(2) @Max(10) Integer cantidadPartes,
        @Size(min = 2, max = 10) List<BigDecimal> montos) {
}
