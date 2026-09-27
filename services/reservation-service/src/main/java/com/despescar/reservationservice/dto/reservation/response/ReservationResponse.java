package com.despescar.reservationservice.dto.reservation.response;

import com.despescar.reservationservice.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Long idCarrito;

    private Long packageId;

    private String vueloCodigo;

    private UUID hotelId;

    private ReservationStatus estadoGeneral;

    private Long segundosRestantes;

    private List<AsientoDetalleDTO> asientos;

    @Data
    @Builder
    public static class AsientoDetalleDTO {
        private String asientoIda;
        private String asientoVuelta;

        private Long pagadorId;
        private java.math.BigDecimal precioCobrado;
        private String estadoPago;

        private String nombrePasajero;
        private String dniPasaporte;

        private String tarifaNombre;
    }

}