package com.despescar.koiiaservice.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Datos de viaje extraídos por el LLM a partir de un mensaje libre del usuario.
 * Todos los campos son opcionales: el LLM solo completa lo que el usuario mencionó.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedTravelInfo(
        BigDecimal budget,
        Integer travelers,
        String destination,
        String origin,
        String travelStyle,
        Integer nights,
        String month
) {
}
