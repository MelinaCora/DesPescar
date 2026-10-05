package com.despescar.reservationservice.dto.pagos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Lo que payment-service reembolsó de la reserva y cuántos pagos quedaron para hacer a mano. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReembolsoReservaResponse(BigDecimal reembolsado, int fallidos) {
}
