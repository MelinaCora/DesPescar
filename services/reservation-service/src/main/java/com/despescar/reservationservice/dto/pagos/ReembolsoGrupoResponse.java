package com.despescar.reservationservice.dto.pagos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Resumen de los reembolsos de un grupo que hizo payment-service (CB5). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReembolsoGrupoResponse(int reembolsados, int cancelados, int fallidos) {
}
