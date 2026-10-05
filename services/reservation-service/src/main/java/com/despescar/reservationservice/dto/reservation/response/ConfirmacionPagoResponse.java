package com.despescar.reservationservice.dto.reservation.response;

/**
 * Respuesta de payment-confirmed (C3, D8) y de pago-confirmado de una parte (CB3). payment-service
 * toma CONFIRMADA y PARTE_PAGADA como éxito y reembolsa con cualquier otro estado (D6, D7, D-b10).
 */
public record ConfirmacionPagoResponse(String estado, String motivo, String mensaje) {

    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String CANCELADA = "CANCELADA";
    public static final String RECHAZADA = "RECHAZADA";
    /** Pago en grupo: la parte quedó pagada y todavía faltan otras (D-b10). */
    public static final String PARTE_PAGADA = "PARTE_PAGADA";

    public static final String PAGO_TARDIO_SIN_DISPONIBILIDAD = "PAGO_TARDIO_SIN_DISPONIBILIDAD";
    public static final String SIN_DISPONIBILIDAD = "SIN_DISPONIBILIDAD";
    public static final String RESERVA_CANCELADA = "RESERVA_CANCELADA";
    public static final String MONTO_NO_COINCIDE = "MONTO_NO_COINCIDE";
    public static final String DATOS_INCOMPLETOS = "DATOS_INCOMPLETOS";
    /** La reserva ya está confirmada por otro pago (otro tokenPago): este se reembolsa. */
    public static final String PAGO_DUPLICADO = "PAGO_DUPLICADO";
    /** La reserva se paga en grupo: un pago de un solo pagador no la confirma (D-b12). */
    public static final String PAGO_EN_GRUPO = "PAGO_EN_GRUPO";
    public static final String PARTE_NO_ES_DEL_PAGADOR = "PARTE_NO_ES_DEL_PAGADOR";
    public static final String PARTE_NO_ENCONTRADA = "PARTE_NO_ENCONTRADA";
    /** Grupo cerrado sin motivo guardado (no debería pasar; se reembolsa igual). */
    public static final String GRUPO_CERRADO = "GRUPO_CERRADO";

    public static ConfirmacionPagoResponse confirmada() {
        return new ConfirmacionPagoResponse(CONFIRMADA, null, "Reserva confirmada.");
    }

    public static ConfirmacionPagoResponse partePagada(String mensaje) {
        return new ConfirmacionPagoResponse(PARTE_PAGADA, null, mensaje);
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
