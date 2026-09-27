package com.despescar.reservationservice.dto.reservation.request;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class SplitPaymentSetupRequest {

    private Long solicitanteId; // ID del creador de la reserva
    private List<PayerAssignationDTO> asignaciones;

    @Data
    public static class PayerAssignationDTO {
        private UUID asientoIda; // Usamos el asiento para identificar qué parte está pagando
        private Long pagadorId;    // Si el amigo ya está registrado en DesPescar
        private String pagadorEmail; // Para enviarle un link de pago si no tiene cuenta
    }
}