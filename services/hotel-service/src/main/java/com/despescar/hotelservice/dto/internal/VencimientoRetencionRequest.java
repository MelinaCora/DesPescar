package com.despescar.hotelservice.dto.internal;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Nuevo vencimiento de una retención (CB1): el plazo del pago en grupo o, al compensar, el anterior. */
public record VencimientoRetencionRequest(@NotNull Instant expiraEn) {
}
