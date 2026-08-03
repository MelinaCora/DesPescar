package com.despescar.reservationservice.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ReservaResponseDTO {

    private Long idCarrito;
    private String vueloCodigo;
    private String estadoGeneral;
    private Long segundosRestantes;
    private List<AsientoDetalleDTO> asientos;

    @Data
    @Builder
    public static class AsientoDetalleDTO {
        private String numeroAsiento;
        private Long usuarioId;
        private Long pagadorId;
        private Double precio;
        private String estadoPago;
        private String nombrePasajero;
        private String dniPasaporte;
    }

}
