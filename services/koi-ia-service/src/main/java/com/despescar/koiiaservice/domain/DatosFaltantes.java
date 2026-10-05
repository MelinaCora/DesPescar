package com.despescar.koiiaservice.domain;

import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.UserIntent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Decide qué datos faltan según la intención (spec 3.7). Un valor inválido (fecha pasada,
 * vuelta antes de la ida, 0 viajeros) cuenta como faltante para que KOI lo vuelva a pedir. Con
 * el destino abierto solo hace falta el presupuesto: origen, viajeros y fechas tienen supuestos.
 */
public final class DatosFaltantes {

    private DatosFaltantes() {
    }

    public static List<MissingInfoField> calcular(DatosViaje d, LocalDate hoy) {
        UserIntent intencion = d.intencionEfectiva();
        boolean conVuelo = intencion != UserIntent.SOLO_HOTEL;
        boolean conHotel = intencion != UserIntent.SOLO_VUELO;
        List<MissingInfoField> faltan = new ArrayList<>();

        if (d.esDestinoAbierto()) {
            if (!presupuestoValido(d.presupuesto())) {
                faltan.add(MissingInfoField.BUDGET);
            }
            return faltan;
        }
        if (conHotel && !presupuestoValido(d.presupuesto())) {
            faltan.add(MissingInfoField.BUDGET);
        }
        if (d.viajeros() == null || d.viajeros() < 1 || d.viajeros() > DatosViaje.MAX_VIAJEROS) {
            faltan.add(MissingInfoField.TRAVELERS);
        }
        if (conVuelo && d.origen() == null) {
            faltan.add(MissingInfoField.ORIGIN);
        }
        if (d.destino() == null) {
            faltan.add(MissingInfoField.DESTINATION);
        }
        if (d.fechaIda() == null || d.fechaIda().isBefore(hoy)) {
            faltan.add(d.fechaIda() == null && d.mesIda() != null
                    ? MissingInfoField.DEPARTURE_DAY : MissingInfoField.DEPARTURE_DATE);
        }
        boolean dioVuelta = d.fechaVuelta() != null || d.noches() != null;
        if ((conHotel || dioVuelta) && !vueltaValida(d)) {
            faltan.add(MissingInfoField.RETURN_OR_NIGHTS);
        }
        return faltan;
    }

    private static boolean presupuestoValido(BigDecimal presupuesto) {
        return presupuesto != null && presupuesto.signum() > 0;
    }

    private static boolean vueltaValida(DatosViaje d) {
        if (d.noches() != null && (d.noches() < 1 || d.noches() > DatosViaje.MAX_NOCHES)) {
            return false;
        }
        if (d.fechaIda() == null) {
            return d.fechaVuelta() != null || d.noches() != null;
        }
        LocalDate vuelta = d.vueltaEfectiva();
        return vuelta != null && vuelta.isAfter(d.fechaIda())
                && ChronoUnit.DAYS.between(d.fechaIda(), vuelta) <= DatosViaje.MAX_NOCHES;
    }
}
