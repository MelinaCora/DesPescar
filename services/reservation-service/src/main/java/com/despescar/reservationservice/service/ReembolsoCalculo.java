package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.TramoPolitica;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cuánto se devuelve al cancelar una reserva confirmada, sin base de datos (spec 3.4). Cada estadía
 * usa la copia de la política del hotel guardada al reservar, con las horas que faltan para su
 * check-in en la zona horaria del hotel. El vuelo usa {@link #POLITICA_VUELO}.
 */
public final class ReembolsoCalculo {

    public static final String VUELO = "VUELO";
    public static final String ESTADIA = "ESTADIA";

    /**
     * Regla del vuelo mientras flight-service no informe la política de cada aerolínea: se devuelve
     * todo cancelando con más de {@link #HORAS_VUELO} horas de anticipación y nada después. Cuando la
     * política de la aerolínea se copie a la reserva, este es el único lugar a cambiar.
     */
    static final int HORAS_VUELO = 24;
    static final int PORCENTAJE_VUELO = 100;

    private ReembolsoCalculo() {
    }

    /** estadiaId es null en el ítem del vuelo. */
    public record Item(String tipo, Long estadiaId, String descripcion, BigDecimal precio, int porcentaje,
                       BigDecimal monto) {
    }

    /** empezo: ya pasó la salida del vuelo o el check-in de alguna estadía (la reserva no se cancela). */
    public record Resultado(List<Item> items, BigDecimal total, boolean empezo) {
    }

    /** El tramo con más horasAntes que todavía se cumple; si ninguno aplica, 0 %. */
    public static int porcentaje(List<TramoPolitica> politica, Duration restante) {
        if (politica == null) {
            return 0;
        }
        return politica.stream()
                .sorted(Comparator.comparingInt(TramoPolitica::getHorasAntes).reversed())
                .filter(t -> restante.compareTo(Duration.ofHours(t.getHorasAntes())) >= 0)
                .map(TramoPolitica::getPorcentajeReembolso)
                .findFirst().orElse(0);
    }

    static int porcentajeVuelo(Duration restante) {
        return restante.compareTo(Duration.ofHours(HORAS_VUELO)) > 0 ? PORCENTAJE_VUELO : 0;
    }

    /** zonaVuelo: la zona en la que está guardada la salida del vuelo (la del reloj del servicio). */
    public static Resultado calcular(Reservation r, Instant ahora, ZoneId zonaVuelo) {
        List<Item> items = new ArrayList<>();
        boolean empezo = false;
        if (CarritoCalculo.tieneVuelo(r)) {
            BigDecimal precio = CarritoCalculo.subtotalVuelo(r);
            int porcentaje = 0;
            if (r.getSalidaVuelo() != null) {
                Duration restante = Duration.between(ahora, r.getSalidaVuelo().atZone(zonaVuelo).toInstant());
                empezo = restante.isZero() || restante.isNegative();
                porcentaje = porcentajeVuelo(restante);
            }
            items.add(new Item(VUELO, null, descripcionVuelo(r), precio, porcentaje, monto(precio, porcentaje)));
        }
        for (EstadiaHotel e : CarritoCalculo.estadiasActivas(r)) {
            Duration restante = Duration.between(ahora, inicio(e));
            empezo = empezo || restante.isZero() || restante.isNegative();
            int porcentaje = porcentaje(e.getPoliticaCancelacion(), restante);
            items.add(new Item(ESTADIA, e.getId(), e.getHotelNombre(), e.getPrecioTotal(), porcentaje,
                    monto(e.getPrecioTotal(), porcentaje)));
        }
        return new Resultado(items, total(items), empezo);
    }

    /** Lo que quedó guardado al cancelar: mismos ítems, con el monto ya decidido en ese momento. */
    public static Resultado guardado(Reservation r) {
        List<Item> items = new ArrayList<>();
        if (CarritoCalculo.tieneVuelo(r)) {
            BigDecimal precio = CarritoCalculo.subtotalVuelo(r);
            BigDecimal monto = escala(r.getMontoReembolsadoVuelo());
            items.add(new Item(VUELO, null, descripcionVuelo(r), precio, porcentajeDe(monto, precio), monto));
        }
        for (EstadiaHotel e : CarritoCalculo.estadiasActivas(r)) {
            BigDecimal monto = escala(e.getMontoReembolsado());
            items.add(new Item(ESTADIA, e.getId(), e.getHotelNombre(), e.getPrecioTotal(),
                    porcentajeDe(monto, e.getPrecioTotal()), monto));
        }
        return new Resultado(items, total(items), false);
    }

    static Instant inicio(EstadiaHotel e) {
        return e.getCheckIn().atTime(e.getHoraCheckIn()).atZone(ZoneId.of(e.getZonaHoraria())).toInstant();
    }

    private static BigDecimal monto(BigDecimal precio, int porcentaje) {
        return precio.multiply(BigDecimal.valueOf(porcentaje)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal total(List<Item> items) {
        return items.stream().map(Item::monto).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static int porcentajeDe(BigDecimal monto, BigDecimal precio) {
        return precio == null || precio.signum() == 0 ? 0
                : monto.multiply(BigDecimal.valueOf(100)).divide(precio, 0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal escala(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static String descripcionVuelo(Reservation r) {
        return r.getTarifasVuelo() == null ? "Vuelo" : "Vuelo · " + r.getTarifasVuelo();
    }
}
