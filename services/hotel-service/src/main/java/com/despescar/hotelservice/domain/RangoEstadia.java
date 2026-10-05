package com.despescar.hotelservice.domain;

import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** Estadía de checkIn (incluido) a checkOut (excluido): ocupa las noches [checkIn, checkOut). */
public record RangoEstadia(LocalDate checkIn, LocalDate checkOut) {

    public static final int MAX_NOCHES = 30;

    /** Vacío si no se pasaron fechas; error si vienen incompletas o no tienen sentido. */
    public static Optional<RangoEstadia> de(LocalDate checkIn, LocalDate checkOut, LocalDate hoy) {
        if (checkIn == null && checkOut == null) {
            return Optional.empty();
        }
        if (checkIn == null || checkOut == null) {
            throw new SolicitudInvalidaException("Indicá check-in y check-out.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new SolicitudInvalidaException("El check-out tiene que ser posterior al check-in.");
        }
        if (checkIn.isBefore(hoy)) {
            throw new SolicitudInvalidaException("El check-in no puede ser una fecha pasada.");
        }
        RangoEstadia rango = new RangoEstadia(checkIn, checkOut);
        if (rango.noches() > MAX_NOCHES) {
            throw new SolicitudInvalidaException("La estadía puede ser de hasta " + MAX_NOCHES + " noches.");
        }
        return Optional.of(rango);
    }

    public long noches() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
