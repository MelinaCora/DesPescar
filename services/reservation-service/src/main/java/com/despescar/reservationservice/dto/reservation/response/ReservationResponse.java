package com.despescar.reservationservice.dto.reservation.response;

import com.despescar.reservationservice.dto.extraBaggage.response.ExtraBaggageResponse;
import com.despescar.reservationservice.enums.ReservationPaymentState;
import com.despescar.reservationservice.enums.ReservationState;
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

    private String vueloCodigo;

    private UUID hotelId;

    private ReservationState estadoGeneral;

    private Long segundosRestantes;

    private List<AsientoDetalleDTO> asientos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AsientoDetalleDTO {

        private String numeroAsiento;

        private Long usuarioId;

        private Long pagadorId;

        private Double precio;

        private ReservationPaymentState estadoPago;

        private String nombrePasajero;

        private String dniPasaporte;

        private List<ExtraBaggageResponse> equipajes;
    }

}