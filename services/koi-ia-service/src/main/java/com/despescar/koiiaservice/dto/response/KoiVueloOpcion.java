package com.despescar.koiiaservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Parte de vuelo de una opción. precio = (tarifa ida + tarifa vuelta) x viajeros, en ARS. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KoiVueloOpcion(
        UUID departureFlightId,
        UUID returnFlightId,
        UUID departureFareId,
        UUID returnFareId,
        String aerolinea,
        String numeroIda,
        String numeroVuelta,
        LocalDateTime salidaIda,
        LocalDateTime llegadaIda,
        LocalDateTime salidaVuelta,
        LocalDateTime llegadaVuelta,
        BigDecimal precio) {
}
