package com.despescar.payment_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de payment-confirmed (contrato C3): estado CONFIRMADA, CANCELADA o RECHAZADA, con
 * motivo (PAGO_TARDIO_SIN_DISPONIBILIDAD, SIN_DISPONIBILIDAD, RESERVA_CANCELADA,
 * MONTO_NO_COINCIDE, DATOS_INCOMPLETOS) y un mensaje legible; o, en un pago en grupo, PARTE_PAGADA
 * cuando faltan partes (CB3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmacionReservaResponse(String estado, String motivo, String mensaje) {

    public static final String CONFIRMADA = "CONFIRMADA";

    /** Una parte quedó pagada y faltan otras (contrato CB3). */
    public static final String PARTE_PAGADA = "PARTE_PAGADA";

    public boolean confirmada() {
        return CONFIRMADA.equals(estado);
    }

    /** Lo que payment-service toma como éxito: la reserva confirmada o la parte pagada (D-b10). */
    public boolean exito() {
        return confirmada() || PARTE_PAGADA.equals(estado);
    }

    /** Respuesta armada por payment-service cuando reservation-service rechaza el pedido (400/404). */
    public static ConfirmacionReservaResponse rechazada(String motivo, String mensaje) {
        return new ConfirmacionReservaResponse("RECHAZADA", motivo, mensaje);
    }
}
