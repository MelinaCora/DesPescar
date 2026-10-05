package com.despescar.hotelservice.dto.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Pedido de reservation-service para tomar unidades de un tipo de habitación (contrato C1). */
public record RetencionRequest(
        @NotNull Long reservaId,
        @NotNull Long usuarioId,
        @NotNull UUID hotelId,
        @NotNull UUID tipoHabitacionId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Min(1) @Max(10) Integer cantidad,
        @NotNull @Min(1) @Max(10) Integer huespedes,
        @NotNull Instant expiraEn) {
}
