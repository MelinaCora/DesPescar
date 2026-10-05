package com.despescar.koiiaservice.recomendador;

import java.math.BigDecimal;
import java.util.UUID;

/** Tipo de habitación con sus unidades libres para las fechas pedidas. */
public record HabitacionCandidata(UUID id, String nombre, int capacidad, BigDecimal precioPorNoche,
                                  int unidadesLibres) {
}
