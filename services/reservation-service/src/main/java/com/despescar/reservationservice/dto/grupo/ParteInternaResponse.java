package com.despescar.reservationservice.dto.grupo;

import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import java.math.BigDecimal;

/** Una parte vista por payment-service antes de cobrarla (CB3). usuarioId es null si está LIBRE. */
public record ParteInternaResponse(
        Long reservaId,
        int numero,
        Long usuarioId,
        BigDecimal monto,
        String moneda,
        EstadoParte estadoParte,
        EstadoGrupo estadoGrupo,
        long segundosRestantes) {
}
