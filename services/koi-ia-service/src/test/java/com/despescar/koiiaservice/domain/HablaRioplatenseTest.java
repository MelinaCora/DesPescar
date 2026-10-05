package com.despescar.koiiaservice.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HablaRioplatenseTest {

    private static final LocalDate LUNES_5_OCT = LocalDate.of(2026, 10, 5);

    @ParameterizedTest
    @CsvSource({
            "'che Koi tengo 2 palos, ofreceme vuelos y hoteles', 2000000",
            "'tengo dos palos para gastar', 2000000",
            "'medio palo', 500000",
            "'un palo y medio', 1500000",
            "'tengo palo y medio', 1500000",
            "'dos palos y medio', 2500000",
            "'300 lucas', 300000",
            "'300 mil pesos', 300000",
            "'1.500 lucas', 1500000",
            "'1,5M', 1500000",
            "'1.5 millones', 1500000",
            "'2M', 2000000",
            "'800k', 800000",
            "'un millón y medio', 1500000",
            "'millon y medio', 1500000",
            "'un millón', 1000000",
            "'3 millones', 3000000",
            "'$ 1.200.000', 1200000",
            "'tengo 2000000 pesos', 2000000",
            "'somos 2, tenemos 3 palos para un finde', 3000000"
    })
    void entiendePlataColoquial(String texto, String esperado) {
        assertEquals(Optional.of(new BigDecimal(esperado).setScale(2)), HablaRioplatense.presupuesto(texto));
    }

    @ParameterizedTest
    @ValueSource(strings = {"somos 2", "3 noches", "hola Koi", "el 19 de noviembre", "2 personas", "un finde"})
    void sinPlataNoInventa(String texto) {
        assertTrue(HablaRioplatense.presupuesto(texto).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "'somos 2, tenemos 3 palos', 2",
            "'somos dos', 2",
            "'somos cuatro', 4",
            "'voy con mi novia', 2",
            "'con mi pareja a donde sea', 2",
            "'con mis viejos', 3",
            "'viajo solo', 1",
            "'viajo sola', 1"
    })
    void entiendeCuantosViajan(String texto, int esperado) {
        assertEquals(Optional.of(esperado), HablaRioplatense.viajeros(texto));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tengo 2 palos", "con mis amigos", "con la familia", "somos un montón"})
    void siNoEsClaroCuantosViajanNoDiceNada(String texto) {
        assertTrue(HablaRioplatense.viajeros(texto).isEmpty());
    }

    @Test
    void elFindeEsDeViernesADomingo() {
        Optional<HablaRioplatense.Finde> finde = HablaRioplatense.finde("tenemos 3 palos para un finde", LUNES_5_OCT);
        assertEquals(LocalDate.of(2026, 10, 9), finde.orElseThrow().ida());
        assertEquals(2, finde.orElseThrow().noches());
        // Un viernes, "el finde" es el que viene, no hoy
        assertEquals(LocalDate.of(2026, 10, 16),
                HablaRioplatense.finde("el fin de semana", LocalDate.of(2026, 10, 9)).orElseThrow().ida());
    }

    @ParameterizedTest
    @ValueSource(strings = {"quiero ir a Bariloche", "el finde del 20 de noviembre", "3 noches"})
    void sinFindeClaroNoFijaFechas(String texto) {
        assertTrue(HablaRioplatense.finde(texto, LUNES_5_OCT).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "che Koi tengo 2 palos, ofreceme vuelos y hoteles o algún viaje",
            "sorprendeme",
            "a donde sea",
            "qué me recomendás con 500 lucas",
            "ofreceme algo",
            "proponeme algún destino"
    })
    void detectaCuandoPideOpcionesSinDestino(String texto) {
        assertTrue(HablaRioplatense.quiereExplorar(texto), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"quiero ir a Bariloche", "somos 2", "el 19 de noviembre", "tengo 2 palos"})
    void noConfundeUnPedidoConcretoConExplorar(String texto) {
        assertFalse(HablaRioplatense.quiereExplorar(texto), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gracias", "Muchas gracias!", "mil gracias", "genial gracias", "te agradezco",
            "gracias koi 🐟", "buenísimo, gracias"})
    void detectaUnAgradecimiento(String texto) {
        assertEquals(Optional.of(HablaRioplatense.Cortesia.AGRADECE), HablaRioplatense.cortesia(texto), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"buenísimo", "joya", "dale", "ok", "listo", "Perfecto!", "dale, genial"})
    void detectaUnaAprobacion(String texto) {
        assertEquals(Optional.of(HablaRioplatense.Cortesia.ASIENTE), HablaRioplatense.cortesia(texto), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"chau", "nos vemos", "hasta luego", "adiós", "gracias, chau!"})
    void detectaUnaDespedida(String texto) {
        assertEquals(Optional.of(HablaRioplatense.Cortesia.DESPIDE), HablaRioplatense.cortesia(texto), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hola", "buenas", "che koi", "Hola Koi!"})
    void detectaUnSaludoSolo(String texto) {
        assertEquals(Optional.of(HablaRioplatense.Cortesia.SALUDA), HablaRioplatense.cortesia(texto), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gracias, pero somos 3", "dale, con 2 palos", "ok, a Bariloche", "listo, el 19 de noviembre",
            "gracias 3", "dale, ofreceme algo", "hola, quiero ir a Mendoza", "no gracias, mejor otro destino",
            "perfecto, para un finde", "", "3"})
    void siElMensajeTraeAlgoMasNoEsCortesia(String texto) {
        assertTrue(HablaRioplatense.cortesia(texto).isEmpty(), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mostrame de nuevo las opciones", "¿cuáles eran?", "las opciones", "repetime",
            "volvé a mostrar", "pasame otra vez las opciones", "a ver de nuevo"})
    void detectaCuandoPideVerLasOpcionesDeNuevo(String texto) {
        assertTrue(HablaRioplatense.quiereVerDeNuevo(texto), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gracias", "ofreceme algo", "mostrame opciones para 3", "las opciones para un finde",
            "mostrame de nuevo pero con 3 palos", "quiero ir a Bariloche", "busca de nuevo con otras fechas"})
    void noConfundeOtroPedidoConVerDeNuevo(String texto) {
        assertFalse(HablaRioplatense.quiereVerDeNuevo(texto), texto);
    }
}
