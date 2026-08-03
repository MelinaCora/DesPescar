package com.despescar.reservationservice.dto;

import com.despescar.reservationservice.enums.EstadoPagoReserva;
import com.despescar.reservationservice.enums.EstadoReserva;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaResponseDTO {

    private Long idCarrito;
    private String vueloCodigo;
    private EstadoReserva estadoGeneral;
    private Long segundosRestantes;
    private List<AsientoDetalleDTO> asientos;

    @Data
    @Builder
    public static class AsientoDetalleDTO {
        private String numeroAsiento;
        private Long usuarioId;
        private Long pagadorId;
        private Double precio;
        private EstadoPagoReserva estadoPago;
        private String nombrePasajero;
        private String dniPasaporte;
    }

}
