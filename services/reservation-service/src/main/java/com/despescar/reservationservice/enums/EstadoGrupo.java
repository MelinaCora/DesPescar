package com.despescar.reservationservice.enums;

/** Estado de un pago en grupo (D-b2). */
public enum EstadoGrupo {
    /** Esperando que se sumen y paguen. */
    ABIERTO,
    /** Todas las partes pagadas; confirmando estadías y asientos. */
    COMPLETO,
    /** La reserva quedó confirmada. */
    CONFIRMADO,
    /** Cancelado por el organizador, por un monto que no coincide o sin lugar al confirmar. */
    CANCELADO,
    /** Pasó el plazo sin que pagaran todos. */
    VENCIDO
}
