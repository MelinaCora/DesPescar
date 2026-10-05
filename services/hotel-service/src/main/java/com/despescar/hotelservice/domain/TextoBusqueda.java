package com.despescar.hotelservice.domain;

import java.text.Normalizer;
import java.util.Locale;

/** Normaliza texto para comparar búsquedas: sin tildes, minúsculas y sin espacios en los bordes. */
public final class TextoBusqueda {

    private TextoBusqueda() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }
}
