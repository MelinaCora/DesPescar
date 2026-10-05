package com.despescar.hotelservice.domain;

import java.math.BigDecimal;
import java.util.List;

/** Lo que cuesta y si se puede alojar a los huéspedes en un tipo de habitación para un rango. */
public record Cotizacion(int unidadesLibres, int habitacionesNecesarias, BigDecimal precioTotal, boolean disponible) {

    public static Cotizacion calcular(int unidadesTotales, int capacidad, BigDecimal precioPorNoche,
                                      List<Disponibilidad.Ocupacion> ocupaciones, RangoEstadia rango,
                                      int huespedes) {
        int libres = Disponibilidad.unidadesLibres(unidadesTotales, ocupaciones, rango.checkIn(), rango.checkOut());
        int necesarias = Disponibilidad.habitacionesNecesarias(huespedes, capacidad);
        BigDecimal precio = PrecioEstadia.total(precioPorNoche, rango, necesarias);
        return new Cotizacion(libres, necesarias, precio, libres >= necesarias);
    }
}
