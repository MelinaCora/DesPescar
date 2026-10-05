package com.despescar.reservationservice.dto.carrito;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Un elemento de PUT /{id}/titulares. Se valida en CarritoService con el Validator de Jakarta (D30):
 * validar elementos de una lista en el controller da otro formato de error.
 */
public record TitularRequest(
        @NotNull Long estadiaId,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 20) String dni,
        @NotBlank @Pattern(regexp = "^[0-9+()\\-\\s]{6,30}$", message = "no es un teléfono válido") String telefono) {
}
