package com.despescar.koiiaservice.domain;

import com.despescar.koiiaservice.enums.UserIntent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Lo que se sabe del viaje. Cualquier campo puede ser null. mesIda se usa cuando el usuario dio
 * solo el mes; fechaVuelta y noches son alternativas (la última que llega reemplaza a la otra).
 */
public record DatosViaje(
        UserIntent intencion,
        BigDecimal presupuesto,
        Integer viajeros,
        String origen,
        String destino,
        LocalDate fechaIda,
        YearMonth mesIda,
        LocalDate fechaVuelta,
        Integer noches) {

    public static final int MAX_VIAJEROS = 9;
    public static final int MAX_NOCHES = 30;

    public static DatosViaje vacio() {
        return new DatosViaje(null, null, null, null, null, null, null, null, null);
    }

    /** Suma lo nuevo a lo que ya se sabía: lo no nulo de {@code nuevos} gana. */
    public DatosViaje combinar(DatosViaje nuevos) {
        UserIntent intent = nuevos.intencion() != null && nuevos.intencion() != UserIntent.UNKNOWN
                ? nuevos.intencion() : intencion;
        LocalDate ida = nuevos.fechaIda() != null ? nuevos.fechaIda()
                : nuevos.mesIda() != null ? null : fechaIda;
        YearMonth mes = nuevos.fechaIda() != null ? null
                : nuevos.mesIda() != null ? nuevos.mesIda() : mesIda;
        LocalDate vuelta = nuevos.fechaVuelta() != null ? nuevos.fechaVuelta()
                : nuevos.noches() != null ? null : fechaVuelta;
        Integer cantidadNoches = nuevos.noches() != null ? nuevos.noches()
                : nuevos.fechaVuelta() != null ? null : noches;
        return new DatosViaje(intent,
                primero(nuevos.presupuesto(), presupuesto),
                primero(nuevos.viajeros(), viajeros),
                primero(texto(nuevos.origen()), origen),
                primero(texto(nuevos.destino()), destino),
                ida, mes, vuelta, cantidadNoches);
    }

    public UserIntent intencionEfectiva() {
        return intencion == null || intencion == UserIntent.UNKNOWN ? UserIntent.COMBO : intencion;
    }

    /** La fecha de vuelta dada o, si se dieron noches, la ida más esas noches. */
    public LocalDate vueltaEfectiva() {
        if (fechaVuelta != null) {
            return fechaVuelta;
        }
        if (fechaIda != null && noches != null) {
            return fechaIda.plusDays(noches);
        }
        return null;
    }

    private static <T> T primero(T nuevo, T anterior) {
        return nuevo != null ? nuevo : anterior;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
