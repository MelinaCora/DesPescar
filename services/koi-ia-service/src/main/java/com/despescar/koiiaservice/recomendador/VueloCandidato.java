package com.despescar.koiiaservice.recomendador;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Un vuelo del catálogo con su tarifa más barata (precio por pasajero, ARS). */
public record VueloCandidato(
        UUID flightId,
        UUID fareId,
        String aerolinea,
        String numero,
        LocalDateTime salida,
        LocalDateTime llegada,
        BigDecimal precioTarifa) {
}
