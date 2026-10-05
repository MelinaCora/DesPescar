package com.despescar.hotelservice.dto.internal;

import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.entity.EstadoRetencion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Retención con lo que reservation-service copia en la estadía: precio, política y horario. */
public record RetencionResponse(
        UUID retencionId,
        UUID hotelId,
        String hotelNombre,
        String ciudad,
        UUID tipoHabitacionId,
        String tipoHabitacionNombre,
        LocalDate checkIn,
        LocalDate checkOut,
        long noches,
        int cantidad,
        int huespedes,
        BigDecimal precioTotal,
        String moneda,
        LocalTime horaCheckIn,
        String zonaHoraria,
        List<TramoDto> politicaCancelacion,
        EstadoRetencion estado,
        Instant expiraEn) {
}
