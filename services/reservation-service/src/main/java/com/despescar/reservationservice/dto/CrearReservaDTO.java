package com.despescar.reservationservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CrearReservaDTO {

    private Long creadorId;
    private String vueloCodigo;
    private List<AsientoSeleccionadoDTO> asientos;

    @Data
    public static class AsientoSeleccionadoDTO {
        private String numeroAsiento;
        private Long usuarioId;
        private Long pagadorId;
    }

}
