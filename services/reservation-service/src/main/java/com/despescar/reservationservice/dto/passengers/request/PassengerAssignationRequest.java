package com.despescar.reservationservice.dto.passengers.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/** PUT /{id}/passengers (contrato C4). Si el front viejo manda precioTarifa, se ignora. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PassengerAssignationRequest {

    @NotEmpty
    @Valid
    private List<PassengerItemDTO> pasajeros;

    /** Contacto de quien compra. Opcional: si no llega, se conserva el que ya tenía el carrito. */
    @Email
    @Size(max = 120)
    private String contactoEmail;

    @Pattern(regexp = "^[0-9+()\\-\\s]{6,30}$", message = "no es un teléfono válido")
    private String contactoTelefono;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PassengerItemDTO {
        @NotBlank
        @Size(max = 100)
        private String nombreCompleto;

        @NotBlank
        @Size(max = 20)
        private String dniPasaporte;
        /** DNI o PASAPORTE. */
        @Pattern(regexp = "^(DNI|PASAPORTE)$", message = "debe ser DNI o PASAPORTE")
        private String tipoDocumento;
        @Past(message = "la fecha de nacimiento tiene que ser anterior a hoy")
        private LocalDate fechaNacimiento;
        /** F, M o X. */
        @Pattern(regexp = "^[FMX]$", message = "debe ser F, M o X")
        private String genero;
        @Size(max = 60)
        private String nacionalidad;

        /** UUID del asiento de ida, bloqueado por el usuario en el mapa. */
        @NotNull
        private UUID asientoIda;

        /** Siempre null en E2 (D10). */
        private UUID asientoVuelta;

        /** Informativos: la tarifa y su precio salen del carrito. */
        private UUID tarifaId;
        private String tarifaNombre;
    }
}
