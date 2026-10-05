package com.despescar.hotelservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PrecioEstadia {

    private PrecioEstadia() {
    }

    public static BigDecimal total(BigDecimal precioPorNoche, RangoEstadia rango, int cantidad) {
        return precioPorNoche
                .multiply(BigDecimal.valueOf(rango.noches()))
                .multiply(BigDecimal.valueOf(cantidad))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
