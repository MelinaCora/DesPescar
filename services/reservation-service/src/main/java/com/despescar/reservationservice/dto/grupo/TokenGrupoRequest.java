package com.despescar.reservationservice.dto.grupo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El token del enlace viaja en el cuerpo, no en la URL (D-b6). */
public record TokenGrupoRequest(@NotBlank @Size(max = 64) String token) {
}
