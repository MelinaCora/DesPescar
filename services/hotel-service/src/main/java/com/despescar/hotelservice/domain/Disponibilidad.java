package com.despescar.hotelservice.domain;

import java.time.LocalDate;
import java.util.List;

/** Cálculos de disponibilidad de un tipo de habitación. */
public final class Disponibilidad {

    /** Unidades tomadas por una retención activa durante [checkIn, checkOut). */
    public record Ocupacion(LocalDate checkIn, LocalDate checkOut, int cantidad) {
    }

    private Disponibilidad() {
    }

    /** Unidades libres durante toda la estadía: las de la noche más ocupada. */
    public static int unidadesLibres(int unidadesTotales, List<Ocupacion> ocupaciones,
                                     LocalDate checkIn, LocalDate checkOut) {
        int minimoLibre = unidadesTotales;
        for (LocalDate noche = checkIn; noche.isBefore(checkOut); noche = noche.plusDays(1)) {
            int ocupadas = 0;
            for (Ocupacion ocupacion : ocupaciones) {
                if (!noche.isBefore(ocupacion.checkIn()) && noche.isBefore(ocupacion.checkOut())) {
                    ocupadas += ocupacion.cantidad();
                }
            }
            minimoLibre = Math.min(minimoLibre, unidadesTotales - ocupadas);
        }
        return Math.max(0, minimoLibre);
    }

    public static int habitacionesNecesarias(int huespedes, int capacidad) {
        return (huespedes + capacidad - 1) / capacidad;
    }
}
