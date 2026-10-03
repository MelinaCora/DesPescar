package com.despescar.reservationservice.dto.passengers.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class PassengerAssignationRequest {
    private List<PassengerItemDTO> pasajeros;

    // IMPORTANTE: Debe ser "public static class" y tener @Data
    @Data
    public static class PassengerItemDTO {
        private String nombreCompleto;
        private String dniPasaporte;
        private UUID asientoIda;
        private UUID asientoVuelta;
        private UUID tarifaId;
        private String tarifaNombre;
        private BigDecimal precioTarifa;
    }
}