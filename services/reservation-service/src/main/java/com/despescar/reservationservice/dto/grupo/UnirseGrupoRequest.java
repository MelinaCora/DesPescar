package com.despescar.reservationservice.dto.grupo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sumarse por enlace (D-b7). El apodo es opcional y es lo único que el grupo ve de quien se suma. */
public record UnirseGrupoRequest(
        @NotBlank @Size(max = 64) String token,
        @Size(max = 30) @Pattern(regexp = "^[\\p{L}\\p{N} .'-]*$") String apodo) {
}
