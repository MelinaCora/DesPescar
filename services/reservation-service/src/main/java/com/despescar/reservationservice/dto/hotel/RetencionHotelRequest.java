package com.despescar.reservationservice.dto.hotel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Pedido a hotel-service para retener habitaciones (contrato C1). */
public record RetencionHotelRequest(
        Long reservaId,
        Long usuarioId,
        UUID hotelId,
        UUID tipoHabitacionId,
        LocalDate checkIn,
        LocalDate checkOut,
        int cantidad,
        int huespedes,
        Instant expiraEn) {
}
