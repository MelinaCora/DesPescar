package com.despescar.koiiaservice.domain;

import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convierte la respuesta del modelo en una KoiExtraccion. Acepta el JSON rodeado de texto o de
 * un bloque ```json (los modelos a veces lo agregan). Si no hay un objeto JSON válido devuelve
 * vacío y el servicio responde un mensaje amable conservando lo ya extraído.
 */
public final class KoiExtraccionParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private KoiExtraccionParser() {
    }

    public static Optional<KoiExtraccion> parsear(String respuesta) {
        if (respuesta == null) {
            return Optional.empty();
        }
        int inicio = respuesta.indexOf('{');
        int fin = respuesta.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(MAPPER.readValue(respuesta.substring(inicio, fin + 1), KoiExtraccion.class));
        } catch (JacksonException ex) {
            return Optional.empty();
        }
    }
}
