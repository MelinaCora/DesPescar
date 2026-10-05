package com.despescar.koiiaservice.recomendador;

import com.despescar.koiiaservice.dto.response.KoiHotelOpcion;
import com.despescar.koiiaservice.dto.response.KoiVueloOpcion;
import com.despescar.koiiaservice.enums.TipoOpcion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Texto corto de cada opción, armado por plantilla (el modelo no interviene). */
public final class Motivos {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    private Motivos() {
    }

    public static String de(TipoOpcion tipo, KoiVueloOpcion vuelo, KoiHotelOpcion hotel, BigDecimal total,
                            BigDecimal presupuesto) {
        String base = switch (tipo) {
            case COMBO -> "Vuelo de %s y %d★ en %s por %s.".formatted(vuelo.aerolinea(), hotel.estrellas(),
                    hotel.ciudad(), noches(hotel.noches()));
            case VUELO -> "Vuelo de %s, %s.".formatted(vuelo.aerolinea(),
                    vuelo.returnFlightId() == null ? "solo ida" : "ida y vuelta");
            case HOTEL -> "%d★ en %s por %s, %s.".formatted(hotel.estrellas(), hotel.ciudad(),
                    noches(hotel.noches()), habitaciones(hotel.cantidadHabitaciones()));
        };
        if (presupuesto == null) {
            return base;
        }
        BigDecimal diferencia = presupuesto.subtract(total);
        if (diferencia.signum() >= 0) {
            return base + " Te quedan " + pesos(diferencia) + " del presupuesto.";
        }
        return base + " Se pasa por " + pesos(diferencia.negate())
                + " de tu presupuesto, pero es lo más cercano que encontré.";
    }

    public static String pesos(BigDecimal monto) {
        NumberFormat formato = NumberFormat.getIntegerInstance(ES_AR);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return "$ " + formato.format(monto);
    }

    private static String noches(int n) {
        return n == 1 ? "1 noche" : n + " noches";
    }

    private static String habitaciones(int n) {
        return n == 1 ? "1 habitación" : n + " habitaciones";
    }
}
