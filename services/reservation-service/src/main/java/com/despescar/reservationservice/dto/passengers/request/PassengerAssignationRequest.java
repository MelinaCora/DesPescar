package com.despescar.reservationservice.dto.passengers.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PassengerItemDTO {
        @NotBlank
        @Size(max = 100)
        private String nombreCompleto;

        @NotBlank
        @Size(max = 20)
        private String dniPasaporte;

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
