package com.despescar.koiiaservice.domain;

import com.despescar.koiiaservice.enums.MissingInfoField;
import java.time.format.TextStyle;
import java.util.Locale;

/** Texto fijo de cada pregunta: lo que se pregunta lo decide Java, no el modelo. */
public final class KoiPreguntas {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    private KoiPreguntas() {
    }

    public static String texto(MissingInfoField campo, DatosViaje datos) {
        return switch (campo) {
            case BUDGET -> "¿Con qué presupuesto total en pesos contás para el viaje?";
            case TRAVELERS -> "¿Cuántas personas viajan? (hasta " + DatosViaje.MAX_VIAJEROS + ")";
            case ORIGIN -> "¿Desde qué ciudad salís?";
            case DESTINATION -> "¿A qué ciudad querés ir?";
            case DEPARTURE_DATE -> "¿Qué día querés salir? Decime la fecha (por ejemplo, 19 de noviembre).";
            case DEPARTURE_DAY -> "¿Qué día de "
                    + datos.mesIda().getMonth().getDisplayName(TextStyle.FULL, ES_AR) + " querés salir?";
            case RETURN_OR_NIGHTS -> "¿Cuándo volvés? Podés decirme la fecha o cuántas noches te quedás.";
        };
    }
}
