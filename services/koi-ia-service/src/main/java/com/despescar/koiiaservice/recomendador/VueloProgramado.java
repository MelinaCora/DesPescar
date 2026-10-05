package com.despescar.koiiaservice.recomendador;

import java.time.LocalDate;

/** Un vuelo del catálogo reducido a su ruta y su día: sirve para saber qué fechas tienen vuelo. */
public record VueloProgramado(String origen, String destino, LocalDate fecha) {
}
