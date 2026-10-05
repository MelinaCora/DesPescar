package com.despescar.payment_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de payment-confirmed (contrato C3): estado CONFIRMADA, CANCELADA o RECHAZADA, con
 * motivo (PAGO_TARDIO_SIN_DISPONIBILIDAD, SIN_DISPONIBILIDAD, RESERVA_CANCELADA,
 * MONTO_NO_COINCIDE, DATOS_INCOMPLETOS) y un mensaje legible.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmacionReservaResponse(String estado, String motivo, String mensaje) {

    public static final String CONFIRMADA = "CONFIRMADA";

    public boolean confirmada() {
        return CONFIRMADA.equals(estado);
    }

    /** Respuesta armada por payment-service cuando reservation-service rechaza el pedido (400/404). */
    public static ConfirmacionReservaResponse rechazada(String motivo, String mensaje) {
        return new ConfirmacionReservaResponse("RECHAZADA", motivo, mensaje);
    }
}
