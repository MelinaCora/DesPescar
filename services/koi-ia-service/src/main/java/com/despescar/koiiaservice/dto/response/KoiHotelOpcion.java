package com.despescar.koiiaservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Parte de hotel de una opción. precio = precio por noche x noches x habitaciones, en ARS. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KoiHotelOpcion(
        UUID hotelId,
        String hotelNombre,
        String ciudad,
        int estrellas,
        String imagen,
        UUID tipoHabitacionId,
        String tipoHabitacionNombre,
        LocalDate checkIn,
        LocalDate checkOut,
        int noches,
        int cantidadHabitaciones,
        int huespedes,
        BigDecimal precio) {
}
