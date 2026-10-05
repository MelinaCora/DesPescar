package com.despescar.koiiaservice.recomendador;

import com.despescar.koiiaservice.domain.DatosViaje;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Elige fechas reales para un destino cuando el usuario no las fijó: la primera ida con vuelo
 * que tenga una vuelta a las noches pedidas (o a una cantidad parecida). Función pura sobre los
 * días con vuelo de ida y de vuelta que informa el catálogo.
 */
public final class FechasFlexibles {

    public record Estadia(LocalDate ida, LocalDate vuelta) {
    }

    /** Diferencias de noches que se aceptan, de la más deseable a la menos. */
    private static final int[] AJUSTES_DE_NOCHES = {0, 1, -1, 2, 3};
    /** Cuántas noches de más se llegan a aceptar: hasta ahí conviene mirar las vueltas. */
    public static final int MARGEN_DE_NOCHES = 3;

    private FechasFlexibles() {
    }

    public static Optional<Estadia> elegir(Collection<LocalDate> fechasDeIda, Collection<LocalDate> fechasDeVuelta,
                                           int nochesPreferidas) {
        Set<LocalDate> idas = new TreeSet<>(fechasDeIda);
        Set<LocalDate> vueltas = new HashSet<>(fechasDeVuelta);
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
