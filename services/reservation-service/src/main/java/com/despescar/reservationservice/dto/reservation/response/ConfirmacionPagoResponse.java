package com.despescar.reservationservice.dto.reservation.response;

/**
 * Respuesta de payment-confirmed (contrato C3, D8). Cualquier estado distinto de CONFIRMADA le indica
 * a payment-service que reembolse (D6, D7).
 */
public record ConfirmacionPagoResponse(String estado, String motivo, String mensaje) {

    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String CANCELADA = "CANCELADA";
    public static final String RECHAZADA = "RECHAZADA";

    public static final String PAGO_TARDIO_SIN_DISPONIBILIDAD = "PAGO_TARDIO_SIN_DISPONIBILIDAD";
    public static final String SIN_DISPONIBILIDAD = "SIN_DISPONIBILIDAD";
    public static final String RESERVA_CANCELADA = "RESERVA_CANCELADA";
    public static final String MONTO_NO_COINCIDE = "MONTO_NO_COINCIDE";
    public static final String DATOS_INCOMPLETOS = "DATOS_INCOMPLETOS";
    /** La reserva ya está confirmada por otro pago (otro tokenPago): este se reembolsa. */
    public static final String PAGO_DUPLICADO = "PAGO_DUPLICADO";

    public static ConfirmacionPagoResponse confirmada() {
        return new ConfirmacionPagoResponse(CONFIRMADA, null, "Reserva confirmada.");
    }

    public static ConfirmacionPagoResponse cancelada(String motivo, String mensaje) {
        return new ConfirmacionPagoResponse(CANCELADA, motivo, mensaje);
    }

    public static ConfirmacionPagoResponse rechazada(String motivo, String mensaje) {
        return new ConfirmacionPagoResponse(RECHAZADA, motivo, mensaje);
    }

    public static ConfirmacionPagoResponse duplicado() {
        return rechazada(PAGO_DUPLICADO, "La reserva ya fue pagada con otro pago.");
    }
}
