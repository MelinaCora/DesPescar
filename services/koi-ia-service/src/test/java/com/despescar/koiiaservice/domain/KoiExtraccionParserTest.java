package com.despescar.koiiaservice.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.koiiaservice.enums.UserIntent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class KoiExtraccionParserTest {

    private static final String COMPLETO = """
            {"comentario":"¡Bariloche es hermoso!","fueraDeTema":false,"intencion":"COMBO",
             "presupuesto":1500000,"viajeros":2,"origen":"Buenos Aires","destino":"Bariloche",
             "fechaIda":"2026-11-19","fechaVuelta":null,"noches":3}
            """;

    @Test
    void leeUnJsonLimpio() {
        KoiExtraccion e = KoiExtraccionParser.parsear(COMPLETO).orElseThrow();
        DatosViaje d = e.aDatos();
        assertEquals("¡Bariloche es hermoso!", e.comentario());
        assertFalse(e.esFueraDeTema());
        assertEquals(UserIntent.COMBO, d.intencion());
        assertEquals(0, new BigDecimal("1500000").compareTo(d.presupuesto()));
        assertEquals(2, d.viajeros());
        assertEquals("Buenos Aires", d.origen());
        assertEquals(LocalDate.of(2026, 11, 19), d.fechaIda());
        assertNull(d.fechaVuelta());
        assertEquals(3, d.noches());
    }

    @Test
    void toleraBloquesDeCodigoYTextoAlrededor() {
        String crudo = "Claro, acá va:\n```json\n" + COMPLETO + "\n```\nSaludos";
        assertTrue(KoiExtraccionParser.parsear(crudo).isPresent());
    }

    @Test
    void siNoHayJsonValidoDevuelveVacio() {
        assertTrue(KoiExtraccionParser.parsear(null).isEmpty());
        assertTrue(KoiExtraccionParser.parsear("").isEmpty());
        assertTrue(KoiExtraccionParser.parsear("Hola, ¿a dónde querés ir?").isEmpty());
        assertTrue(KoiExtraccionParser.parsear("{\"comentario\": \"sin cerrar\"").isEmpty());
        assertTrue(KoiExtraccionParser.parsear("{\"viajeros\": \"dos\"}").isEmpty());
    }

    @Test
    void unMesSinDiaQuedaComoMes() {
        DatosViaje d = KoiExtraccionParser.parsear("{\"fechaIda\":\"2026-12\"}").orElseThrow().aDatos();
        assertNull(d.fechaIda());
        assertEquals(YearMonth.of(2026, 12), d.mesIda());
    }

    @Test
    void valoresRarosSeDescartanSinRomperElResto() {
        DatosViaje d = KoiExtraccionParser.parsear("""
                {"intencion":"PAQUETE","fechaIda":"19/11/2026","fechaVuelta":"mañana","viajeros":2,
                 "origen":"  ","destino":"Córdoba","campoQueNoExiste":1}
                """).orElseThrow().aDatos();
        assertNull(d.intencion());
        assertNull(d.fechaIda());
        assertNull(d.mesIda());
        assertNull(d.fechaVuelta());
        assertNull(d.origen());
        assertEquals(2, d.viajeros());
        assertEquals("Córdoba", d.destino());
    }

    @Test
    void elPromptLlevaLaFechaDeHoyYPideSoloJson() {
        String prompt = KoiPrompt.sistema(LocalDate.of(2026, 11, 1));
        assertTrue(prompt.contains("2026-11-01"));
        assertTrue(prompt.contains("\"fueraDeTema\""));
        assertTrue(prompt.contains("SOLO JSON"));
    }
}
