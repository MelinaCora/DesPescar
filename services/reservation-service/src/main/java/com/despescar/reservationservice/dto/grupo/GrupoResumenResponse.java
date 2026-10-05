package com.despescar.reservationservice.dto.grupo;

import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un grupo donde el usuario tiene parte, para el aviso de /carrito (CB2, D-b19). */
public record GrupoResumenResponse(
        Long reservaId,
        String enlaceToken,
        EstadoGrupo estado,
        LocalDateTime venceEn,
        long segundosRestantes,
        boolean soyOrganizador,
        int miParte,
        BigDecimal monto,
        EstadoParte estadoParte,
        String destino) {
}
