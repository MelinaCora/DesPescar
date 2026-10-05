package com.despescar.koiiaservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Qué explorar cuando el destino está abierto. presupuesto, viajeros y origen ya vienen
 * resueltos (con los supuestos aplicados). fechaIda, mesIda y noches son preferencias: si para
 * un destino no hay vuelos en esas fechas se buscan las más cercanas con vuelo.
 */
public record PedidoExploracion(BigDecimal presupuesto, int viajeros, String origen, LocalDate fechaIda,
                                YearMonth mesIda, Integer noches, LocalDate hoy) {
}
