package com.despescar.koiiaservice.recomendador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FechasFlexiblesTest {

    private static final LocalDate D19 = LocalDate.of(2026, 10, 19);
    private static final LocalDate D22 = LocalDate.of(2026, 10, 22);
    private static final LocalDate D26 = LocalDate.of(2026, 10, 26);

    private static final List<VueloProgramado> PROGRAMADOS = List.of(
            new VueloProgramado("AEP", "BRC", D19), new VueloProgramado("AEP", "BRC", D22),
            new VueloProgramado("BRC", "AEP", D22), new VueloProgramado("BRC", "AEP", D26),
            new VueloProgramado("EZE", "MAD", D19), new VueloProgramado("MAD", "EZE", D26));

    @Test
    void eligeLaPrimeraIdaConVueltaALasNochesPedidas() {
        Optional<FechasFlexibles.Estadia> estadia = FechasFlexibles.elegir(PROGRAMADOS, List.of("AEP", "EZE"),
                List.of("BRC"), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 12, 4), 3);
        assertEquals(new FechasFlexibles.Estadia(D19, D22), estadia.orElseThrow());
    }

    @Test
    void siNoHayVueltaJustaPruebaNochesCercanas() {
        // Madrid: ida el 19, vuelta recién el 26 (7 noches); con 5 noches pedidas acepta hasta +3
        assertEquals(new FechasFlexibles.Estadia(D19, D26), FechasFlexibles.elegir(PROGRAMADOS, List.of("EZE"),
                List.of("MAD"), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 12, 4), 5).orElseThrow());
        // con 2 noches pedidas, 7 queda demasiado lejos
        assertTrue(FechasFlexibles.elegir(PROGRAMADOS, List.of("EZE"), List.of("MAD"),
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 12, 4), 2).isEmpty());
    }

    @Test
    void respetaLaVentanaYLosAeropuertos() {
        assertTrue(FechasFlexibles.elegir(PROGRAMADOS, List.of("AEP"), List.of("BRC"),
                LocalDate.of(2026, 10, 23), LocalDate.of(2026, 12, 4), 3).isEmpty());
        assertTrue(FechasFlexibles.elegir(PROGRAMADOS, List.of("AEP"), List.of("MDZ"),
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 12, 4), 3).isEmpty());
    }

    @Test
    void prefiereLaIdaMasCercanaALaPedidaAunqueHayaOtraAntes() {
        assertEquals(new FechasFlexibles.Estadia(D22, D26), FechasFlexibles.elegir(PROGRAMADOS, List.of("AEP"),
                List.of("BRC"), LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 4), 3).orElseThrow());
    }
}
