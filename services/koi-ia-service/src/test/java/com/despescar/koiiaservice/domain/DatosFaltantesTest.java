package com.despescar.koiiaservice.domain;

import static com.despescar.koiiaservice.enums.MissingInfoField.BUDGET;
import static com.despescar.koiiaservice.enums.MissingInfoField.DEPARTURE_DATE;
import static com.despescar.koiiaservice.enums.MissingInfoField.DEPARTURE_DAY;
import static com.despescar.koiiaservice.enums.MissingInfoField.DESTINATION;
import static com.despescar.koiiaservice.enums.MissingInfoField.ORIGIN;
import static com.despescar.koiiaservice.enums.MissingInfoField.RETURN_OR_NIGHTS;
import static com.despescar.koiiaservice.enums.MissingInfoField.TRAVELERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.koiiaservice.enums.UserIntent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatosFaltantesTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 1);
    private static final LocalDate D19 = LocalDate.of(2026, 11, 19);
    private static final LocalDate D22 = LocalDate.of(2026, 11, 22);
    private static final BigDecimal PRESUPUESTO = new BigDecimal("1500000");

    private static DatosViaje completo(UserIntent intencion) {
        return new DatosViaje(intencion, PRESUPUESTO, 2, "Buenos Aires", "Bariloche", D19, null, null, 3, null);
    }

    @Test
    void comboVacioPideTodoEnOrden() {
        DatosViaje vacio = DatosViaje.vacio().combinar(
                new DatosViaje(UserIntent.COMBO, null, null, null, null, null, null, null, null, null));
        assertEquals(List.of(BUDGET, TRAVELERS, ORIGIN, DESTINATION, DEPARTURE_DATE, RETURN_OR_NIGHTS),
                DatosFaltantes.calcular(vacio, HOY));
    }

    @Test
    void intencionDesconocidaSeTrataComoCombo() {
        assertEquals(UserIntent.COMBO, DatosViaje.vacio().intencionEfectiva());
        assertEquals(List.of(BUDGET, TRAVELERS, ORIGIN, DESTINATION, DEPARTURE_DATE, RETURN_OR_NIGHTS),
                DatosFaltantes.calcular(DatosViaje.vacio(), HOY));
    }

    @Test
    void soloVueloNoPidePresupuestoNiVuelta() {
        DatosViaje d = new DatosViaje(UserIntent.SOLO_VUELO, null, null, null, null, null, null, null, null, null);
        assertEquals(List.of(TRAVELERS, ORIGIN, DESTINATION, DEPARTURE_DATE), DatosFaltantes.calcular(d, HOY));
    }

    @Test
    void soloHotelNoPideOrigen() {
        DatosViaje d = new DatosViaje(UserIntent.SOLO_HOTEL, null, null, null, null, null, null, null, null, null);
        assertEquals(List.of(BUDGET, TRAVELERS, DESTINATION, DEPARTURE_DATE, RETURN_OR_NIGHTS),
                DatosFaltantes.calcular(d, HOY));
    }

    @Test
    void conTodoNoFaltaNada() {
        assertTrue(DatosFaltantes.calcular(completo(UserIntent.COMBO), HOY).isEmpty());
        assertTrue(DatosFaltantes.calcular(completo(UserIntent.SOLO_HOTEL), HOY).isEmpty());
        DatosViaje soloIda = new DatosViaje(UserIntent.SOLO_VUELO, null, 1, "Buenos Aires", "Córdoba", D19,
                null, null, null, null);
        assertTrue(DatosFaltantes.calcular(soloIda, HOY).isEmpty());
    }

    @Test
    void soloElMesPideElDia() {
        DatosViaje d = new DatosViaje(UserIntent.COMBO, PRESUPUESTO, 2, "Buenos Aires", "Bariloche", null,
                YearMonth.of(2026, 11), null, 3, null);
        assertEquals(List.of(DEPARTURE_DAY), DatosFaltantes.calcular(d, HOY));
        assertTrue(KoiPreguntas.texto(DEPARTURE_DAY, d).contains("noviembre"));
    }

    @Test
    void valoresInvalidosCuentanComoFaltantes() {
        DatosViaje base = completo(UserIntent.COMBO);
        assertEquals(List.of(BUDGET), DatosFaltantes.calcular(
                base.combinar(new DatosViaje(null, BigDecimal.ZERO, null, null, null, null, null, null, null, null)), HOY));
        assertEquals(List.of(TRAVELERS), DatosFaltantes.calcular(
                base.combinar(new DatosViaje(null, null, 10, null, null, null, null, null, null, null)), HOY));
        assertEquals(List.of(DEPARTURE_DATE), DatosFaltantes.calcular(
                base.combinar(new DatosViaje(null, null, null, null, null, HOY.minusDays(1), null, null, null, null)), HOY));
        assertEquals(List.of(RETURN_OR_NIGHTS), DatosFaltantes.calcular(
                base.combinar(new DatosViaje(null, null, null, null, null, null, null, D19.minusDays(2), null, null)), HOY));
        assertEquals(List.of(RETURN_OR_NIGHTS), DatosFaltantes.calcular(
                base.combinar(new DatosViaje(null, null, null, null, null, null, null, null, 31, null)), HOY));
    }

    @Test
    void soloVueloConUnaVueltaInvalidaLaVuelveAPedir() {
        DatosViaje d = new DatosViaje(UserIntent.SOLO_VUELO, null, 1, "Buenos Aires", "Córdoba", D19, null,
                D19, null, null);
        assertEquals(List.of(RETURN_OR_NIGHTS), DatosFaltantes.calcular(d, HOY));
    }

    @Test
    void combinarConservaLoAnteriorYReemplazaLoNuevo() {
        DatosViaje antes = completo(UserIntent.COMBO);
        DatosViaje despues = antes.combinar(new DatosViaje(UserIntent.UNKNOWN, null, 3, null, null, null, null,
                null, null, null));
        assertEquals(UserIntent.COMBO, despues.intencion());
        assertEquals(3, despues.viajeros());
        assertEquals("Bariloche", despues.destino());
        assertEquals(PRESUPUESTO, despues.presupuesto());
    }

    @Test
    void laVueltaYLasNochesSeReemplazanEntreSi() {
        DatosViaje conNoches = completo(UserIntent.COMBO);
        assertEquals(D22, conNoches.vueltaEfectiva());

        DatosViaje conFecha = conNoches.combinar(new DatosViaje(null, null, null, null, null, null, null,
                D22.plusDays(1), null, null));
        assertNull(conFecha.noches());
        assertEquals(D22.plusDays(1), conFecha.vueltaEfectiva());

        DatosViaje otraVezNoches = conFecha.combinar(new DatosViaje(null, null, null, null, null, null, null,
                null, 2, null));
        assertNull(otraVezNoches.fechaVuelta());
        assertEquals(D19.plusDays(2), otraVezNoches.vueltaEfectiva());
    }

    @Test
    void unaFechaNuevaLimpiaElMesYUnMesNuevoLimpiaLaFecha() {
        DatosViaje conMes = DatosViaje.vacio().combinar(new DatosViaje(null, null, null, null, null, null,
                YearMonth.of(2026, 12), null, null, null));
        DatosViaje conDia = conMes.combinar(new DatosViaje(null, null, null, null, null, D19, null, null, null, null));
        assertNull(conDia.mesIda());
        assertEquals(D19, conDia.fechaIda());

        DatosViaje otroMes = conDia.combinar(new DatosViaje(null, null, null, null, null, null,
                YearMonth.of(2027, 1), null, null, null));
        assertNull(otroMes.fechaIda());
        assertEquals(YearMonth.of(2027, 1), otroMes.mesIda());
    }

    @Test
    void normalizaSinTildesNiMayusculas() {
        assertEquals("cordoba", TextoBusqueda.normalizar("  Córdoba "));
        assertEquals("san carlos de bariloche", TextoBusqueda.normalizar("San Carlos  de BARILOCHE"));
        assertEquals("", TextoBusqueda.normalizar(null));
    }
    @Test
    void conElDestinoAbiertoSoloFaltaElPresupuesto() {
        DatosViaje abierto = DatosViaje.vacio().combinar(
                new DatosViaje(null, null, null, null, null, null, null, null, null, true));
        assertEquals(List.of(BUDGET), DatosFaltantes.calcular(abierto, HOY));
        DatosViaje conPlata = abierto.combinar(
                new DatosViaje(null, PRESUPUESTO, null, null, null, null, null, null, null, null));
        assertTrue(DatosFaltantes.calcular(conPlata, HOY).isEmpty());
        assertTrue(conPlata.esDestinoAbierto());
        // nombrar un destino cierra la exploración y vuelve a pedir lo demás
        DatosViaje cerrado = conPlata.combinar(
                new DatosViaje(null, null, null, null, "Salta", null, null, null, null, null));
        assertEquals(List.of(TRAVELERS, ORIGIN, DEPARTURE_DATE, RETURN_OR_NIGHTS), DatosFaltantes.calcular(cerrado, HOY));
    }
}
