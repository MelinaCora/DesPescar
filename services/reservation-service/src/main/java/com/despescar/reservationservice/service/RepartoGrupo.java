package com.despescar.reservationservice.service;

import com.despescar.reservationservice.exception.BookingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Cuentas del reparto de un pago en grupo, sin base de datos (D-b3). */
public final class RepartoGrupo {

    public static final int MIN_PARTES = 2;
    public static final int MAX_PARTES = 10;
    /** Ninguna parte por debajo de esto, para no generar cobros que el proveedor rechace. */
    public static final BigDecimal MONTO_MINIMO = new BigDecimal("100.00");

    private RepartoGrupo() {
    }

    /** total / partes truncado a centavos; los centavos que sobran van a la parte 1 (el organizador). */
    public static List<BigDecimal> iguales(BigDecimal total, int partes) {
        validarCantidad(partes, total);
        BigDecimal exacto = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = exacto.divide(BigDecimal.valueOf(partes), 2, RoundingMode.DOWN);
        BigDecimal resto = exacto.subtract(base.multiply(BigDecimal.valueOf(partes)));
        List<BigDecimal> montos = new ArrayList<>(partes);
        for (int i = 0; i < partes; i++) {
            montos.add(i == 0 ? base.add(resto) : base);
        }
        return montos;
    }

    public static void validarCantidad(int partes, BigDecimal total) {
        if (partes < MIN_PARTES || partes > MAX_PARTES) {
            throw new BookingException("CANTIDAD_PARTES_INVALIDA",
                    "El pago se puede dividir entre 2 y 10 personas.", HttpStatus.BAD_REQUEST);
        }
        if (total == null || total.compareTo(MONTO_MINIMO.multiply(BigDecimal.valueOf(partes))) < 0) {
            throw new BookingException("PARTES_DEMASIADO_CHICAS",
                    "Con ese total cada parte quedaría por debajo de $100. Probá con menos personas.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Valida los montos que editó el organizador y los devuelve con escala 2: misma cantidad que
     * partes (2 a 10), cada uno con dos decimales como mucho y de al menos $100, y que sumen
     * exactamente el total.
     */
    public static List<BigDecimal> validarMontos(List<BigDecimal> montos, BigDecimal total) {
        if (montos == null) {
            throw new BookingException("VALIDACION", "Faltan los montos de las partes.", HttpStatus.BAD_REQUEST);
        }
        validarCantidad(montos.size(), total);
        List<BigDecimal> normalizados = new ArrayList<>(montos.size());
        for (BigDecimal monto : montos) {
            if (monto == null || monto.stripTrailingZeros().scale() > 2 || monto.compareTo(MONTO_MINIMO) < 0) {
                throw new BookingException("MONTOS_INVALIDOS",
                        "Cada parte tiene que ser de al menos $100 y tener como mucho dos decimales.", HttpStatus.BAD_REQUEST);
            }
            normalizados.add(monto.setScale(2, RoundingMode.UNNECESSARY));
        }
        BigDecimal suma = normalizados.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal exacto = total.setScale(2, RoundingMode.HALF_UP);
        if (suma.compareTo(exacto) != 0) {
            throw new BookingException("MONTOS_NO_SUMAN_TOTAL",
                    "Las partes suman $" + suma.toPlainString() + " y el total es $" + exacto.toPlainString() + ".",
                    HttpStatus.BAD_REQUEST);
        }
        return normalizados;
    }
}
