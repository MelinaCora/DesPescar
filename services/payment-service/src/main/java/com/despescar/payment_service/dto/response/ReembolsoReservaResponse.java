package com.despescar.payment_service.dto.response;

import java.math.BigDecimal;

/** Lo que quedó reembolsado de la reserva por ese motivo y cuántos pagos quedaron para hacer a mano. */
public record ReembolsoReservaResponse(BigDecimal reembolsado, int fallidos) {
}
