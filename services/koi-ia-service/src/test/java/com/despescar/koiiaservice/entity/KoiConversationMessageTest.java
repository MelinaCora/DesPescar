package com.despescar.koiiaservice.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

class KoiConversationMessageTest {

    @Test
    void laColumnaDeOpcionesEntraLasTresOpcionesSerializadas() throws Exception {
        Column columna = KoiConversationMessage.class.getDeclaredField("opcionesJson").getAnnotation(Column.class);
        // En MySQL un String sin length ni Lob queda varchar(255) y el JSON de las opciones no entra.
        assertTrue(columna.length() >= 10000);
    }
}
