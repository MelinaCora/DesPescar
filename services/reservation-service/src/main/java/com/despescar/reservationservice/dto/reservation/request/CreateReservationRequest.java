package com.despescar.reservationservice.dto.reservation.request;

import com.despescar.reservationservice.dto.extraBaggage.request.ExtraBaggageRequest;
import com.despescar.reservationservice.dto.passengers.request.PassengerRequest;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class CreateReservationRequest {

    private Long creadorId;

    private Long packageId;

    private String vueloCodigo;

    private UUID hotelId;

    private List<AsientoSeleccionadoDTO> asientos;


    @Data
    @NoArgsConstructor
    public static class AsientoSeleccionadoDTO {

        private String numeroAsiento;

        private Long usuarioId;

        private Long pagadorId;

        private PassengerRequest pasajero;

        private String dniPasaporte;

        private List<ExtraBaggageRequest> equipajes;
    }
}
