package com.despescar.koiiaservice.domain;

import com.despescar.koiiaservice.enums.UserIntent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Lo que se sabe del viaje. Cualquier campo puede ser null. mesIda se usa cuando el usuario dio
 * solo el mes; fechaVuelta y noches son alternativas (la última que llega reemplaza a la otra).
 * destinoAbierto marca que el usuario pidió opciones sin elegir destino: KOI lo propone en vez
 * de preguntarlo; nombrar un destino lo cierra.
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
        Integer noches,
        Boolean destinoAbierto) {

    public static final int MAX_VIAJEROS = 9;
    public static final int MAX_NOCHES = 30;

    public static DatosViaje vacio() {
        return new DatosViaje(null, null, null, null, null, null, null, null, null, null);
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
        String destinoNuevo = texto(nuevos.destino());
        boolean abierto;
        String destinoFinal;
        if (destinoNuevo != null) {
            abierto = false;
            destinoFinal = destinoNuevo;
        } else if (nuevos.esDestinoAbierto()) {
            abierto = true;
            destinoFinal = null;
        } else {
            abierto = esDestinoAbierto();
            destinoFinal = destino;
        }
        return new DatosViaje(intent,
                primero(nuevos.presupuesto(), presupuesto),
                primero(nuevos.viajeros(), viajeros),
                primero(texto(nuevos.origen()), origen),
                destinoFinal,
                ida, mes, vuelta, cantidadNoches, abierto);
    }

    /**
     * Lo que queda de una búsqueda anterior cuando el usuario pide opciones sin destino: la plata,
     * cuántos viajan y desde dónde. El destino, las fechas y la intención eran de la otra búsqueda.
     */
    public DatosViaje paraBusquedaNueva() {
        return new DatosViaje(null, presupuesto, viajeros, origen, null, null, null, null, null, false);
    }

    public boolean esDestinoAbierto() {
        return Boolean.TRUE.equals(destinoAbierto) && destino == null;
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
