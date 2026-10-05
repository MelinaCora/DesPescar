package com.despescar.koiiaservice.domain;

import java.text.Normalizer;
import java.util.Locale;

/** Normaliza texto para comparar ciudades: minúsculas, sin tildes ni espacios repetidos. */
public final class TextoBusqueda {

    private TextoBusqueda() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.replaceAll("\\s+", " ");
    }
}
