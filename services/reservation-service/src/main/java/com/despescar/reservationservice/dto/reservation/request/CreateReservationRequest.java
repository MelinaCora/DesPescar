package com.despescar.reservationservice.dto.reservation.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CreateReservationRequest {

    private Long creadorId;

    private String vueloCodigo;

    private List<AsientoSeleccionadoDTO> asientos;


    @Data
    @NoArgsConstructor
    public static class AsientoSeleccionadoDTO {

        private String numeroAsiento;

        private Long usuarioId;

        private Long pagadorId;

        private String nombrePasajero;

        private String dniPasaporte;
    }
}
