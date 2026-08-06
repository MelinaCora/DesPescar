package com.despescar.reservationservice.dto.reservation.response;

import com.despescar.reservationservice.enums.ReservationPaymentState;
import com.despescar.reservationservice.enums.ReservationState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Long idCarrito;
    private String vueloCodigo;
    private ReservationState estadoGeneral;
    private Long segundosRestantes;
    private List<AsientoDetalleDTO> asientos;

    @Data
    @Builder
    public static class AsientoDetalleDTO {
        private String numeroAsiento;
        private Long usuarioId;
        private Long pagadorId;
        private Double precio;
        private ReservationPaymentState estadoPago;
        private String nombrePasajero;
        private String dniPasaporte;
    }

}
