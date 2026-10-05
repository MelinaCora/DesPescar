package com.despescar.koiiaservice.domain;

import com.despescar.koiiaservice.enums.UserIntent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Lo que el modelo dice haber entendido de un mensaje. Las fechas llegan como texto y se
 * validan acá: "yyyy-MM-dd" es una fecha, "yyyy-MM" es solo el mes, cualquier otra cosa se
 * descarta.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KoiExtraccion(
        String comentario,
        Boolean fueraDeTema,
        String intencion,
        BigDecimal presupuesto,
        Integer viajeros,
        String origen,
        String destino,
        String fechaIda,
        String fechaVuelta,
        Integer noches,
        Boolean destinoAbierto) {

    public boolean esFueraDeTema() {
        return Boolean.TRUE.equals(fueraDeTema);
    }

    public DatosViaje aDatos() {
        // Escala 2 como la columna de la sesión: así un presupuesto repetido no cuenta como cambio
        BigDecimal monto = presupuesto == null ? null : presupuesto.setScale(2, RoundingMode.HALF_UP);
        return new DatosViaje(intencion(intencion), monto, viajeros, texto(origen), texto(destino),
                fecha(fechaIda), mes(fechaIda), fecha(fechaVuelta), noches, destinoAbierto);
    }

    private static UserIntent intencion(String valor) {
        if (valor == null) {
            return null;
        }
        return switch (valor.trim().toUpperCase()) {
            case "COMBO" -> UserIntent.COMBO;
            case "SOLO_VUELO" -> UserIntent.SOLO_VUELO;
            case "SOLO_HOTEL" -> UserIntent.SOLO_HOTEL;
            default -> null;
        };
    }

    private static LocalDate fecha(String valor) {
        if (valor == null || !valor.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static YearMonth mes(String valor) {
        if (valor == null || !valor.trim().matches("\\d{4}-\\d{2}")) {
            return null;
        }
        try {
            return YearMonth.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
