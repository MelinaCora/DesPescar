package com.despescar.koiiaservice.recomendador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FechasFlexiblesTest {

    private static final LocalDate D19 = LocalDate.of(2026, 10, 19);
    private static final LocalDate D22 = LocalDate.of(2026, 10, 22);
    private static final LocalDate D26 = LocalDate.of(2026, 10, 26);

    @Test
    void eligeLaPrimeraIdaConVueltaALasNochesPedidas() {
        assertEquals(new FechasFlexibles.Estadia(D19, D22),
                FechasFlexibles.elegir(List.of(D22, D19), List.of(D26, D22), 3).orElseThrow());
    }

    @Test
    void siNoHayVueltaJustaPruebaNochesCercanas() {
        // ida el 19, vuelta recién el 26 (7 noches); con 5 noches pedidas acepta hasta +3
        assertEquals(new FechasFlexibles.Estadia(D19, D26),
                FechasFlexibles.elegir(List.of(D19), List.of(D26), 5).orElseThrow());
        // con 2 noches pedidas, 7 queda demasiado lejos
        assertTrue(FechasFlexibles.elegir(List.of(D19), List.of(D26), 2).isEmpty());
    }

    @Test
    void sinIdasOSinVueltasNoHayEstadia() {
        assertTrue(FechasFlexibles.elegir(List.of(), List.of(D22), 3).isEmpty());
        assertTrue(FechasFlexibles.elegir(List.of(D19), List.of(), 3).isEmpty());
    }

    @Test
    void siLaPrimeraIdaNoTieneVueltaPasaALaSiguiente() {
        assertEquals(new FechasFlexibles.Estadia(D22, D26),
                FechasFlexibles.elegir(List.of(D19, D22), List.of(D26), 3).orElseThrow());
    }
}
