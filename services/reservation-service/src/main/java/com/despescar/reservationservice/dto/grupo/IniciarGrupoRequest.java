package com.despescar.reservationservice.dto.grupo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Entre cuántas personas se divide, organizador incluido (D-b3). */
public record IniciarGrupoRequest(@NotNull @Min(2) @Max(10) Integer cantidadPartes) {
}
