package com.despescar.koiiaservice.recomendador;

import com.despescar.koiiaservice.domain.DatosViaje;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Elige fechas reales para un destino cuando el usuario no las fijó: la primera ida de la
 * ventana que tenga vuelo y una vuelta a las noches pedidas (o a una cantidad parecida). Función
 * pura sobre los vuelos programados del catálogo.
 */
public final class FechasFlexibles {

    public record Estadia(LocalDate ida, LocalDate vuelta) {
    }

    /** Diferencias de noches que se aceptan, de la más deseable a la menos. */
    private static final int[] AJUSTES_DE_NOCHES = {0, 1, -1, 2, 3};

    private FechasFlexibles() {
    }

    public static Optional<Estadia> elegir(List<VueloProgramado> programados, Collection<String> origenes,
                                           Collection<String> destinos, LocalDate desde, LocalDate hasta,
                                           int nochesPreferidas) {
        Set<LocalDate> idas = new TreeSet<>();
        Set<LocalDate> vueltas = new TreeSet<>();
        for (VueloProgramado v : programados) {
            if (v.fecha() == null || v.origen() == null || v.destino() == null) {
                continue;
            }
            if (origenes.contains(v.origen()) && destinos.contains(v.destino())
                    && !v.fecha().isBefore(desde) && !v.fecha().isAfter(hasta)) {
                idas.add(v.fecha());
            }
            if (destinos.contains(v.origen()) && origenes.contains(v.destino())) {
                vueltas.add(v.fecha());
            }
        }
        for (LocalDate ida : idas) {
            for (int ajuste : AJUSTES_DE_NOCHES) {
                int noches = nochesPreferidas + ajuste;
                if (noches < 1 || noches > DatosViaje.MAX_NOCHES) {
                    continue;
                }
                LocalDate vuelta = ida.plusDays(noches);
                if (vueltas.contains(vuelta)) {
                    return Optional.of(new Estadia(ida, vuelta));
                }
            }
        }
        return Optional.empty();
    }
}
