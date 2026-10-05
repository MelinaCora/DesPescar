package com.despescar.reservationservice.dto.reservation.response;

import com.despescar.reservationservice.enums.ReservationStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * Vista previa (estado CONFIRMADA) o resultado (estado CANCELADA) de cancelar una reserva entera.
 * reembolsoPendiente: la reserva ya está cancelada y payment-service todavía no confirmó el reembolso.
 */
public record CancelacionResponse(Long reservaId, ReservationStatus estado, BigDecimal reembolsoTotal, String moneda,
                                  boolean pagoEnGrupo, boolean reembolsoPendiente, List<ItemDTO> detalle) {

    /** tipo: VUELO o ESTADIA (con su estadiaId). */
    public record ItemDTO(String tipo, Long estadiaId, String descripcion, BigDecimal precio, int porcentaje,
                          BigDecimal monto) {
    }
}
