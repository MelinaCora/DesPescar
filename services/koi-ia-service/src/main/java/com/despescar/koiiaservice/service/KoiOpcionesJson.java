package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.dto.response.KoiRecommendationResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda las opciones de cada mensaje de KOI como JSON en la tabla de mensajes, para devolverlas
 * con el historial. Jackson 3 trae java.time incorporado y escribe las fechas como texto ISO.
 */
@Component
public class KoiOpcionesJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<KoiRecommendationResponse>> LISTA = new TypeReference<>() {
    };

    public String escribir(List<KoiRecommendationResponse> opciones) {
        if (opciones == null || opciones.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(opciones);
    }

    public List<KoiRecommendationResponse> leer(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, LISTA);
        } catch (JacksonException ex) {
            return List.of();
        }
    }
}
