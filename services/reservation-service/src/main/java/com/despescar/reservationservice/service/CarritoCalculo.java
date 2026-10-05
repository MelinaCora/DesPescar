package com.despescar.reservationservice.service;

import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoItem;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Cuentas del carrito sin base de datos: total, ítems, datos completos y tiempo restante. */
public final class CarritoCalculo {

    public static final String MONEDA = "ARS";

    private CarritoCalculo() {
    }

    public static boolean tieneVuelo(Reservation r) {
        return r.getFlightIds() != null && !r.getFlightIds().isEmpty();
    }

    public static List<EstadiaHotel> estadiasActivas(Reservation r) {
        if (r.getEstadias() == null) {
            return List.of();
        }
        return r.getEstadias().stream().filter(e -> e.getEstado() == EstadoItem.ACTIVA).toList();
    }

    public static boolean pasajerosCargados(Reservation r) {
        return tieneVuelo(r) && r.getDetalles() != null && !r.getDetalles().isEmpty()
                && r.getCantidadPasajeros() != null && r.getDetalles().size() == r.getCantidadPasajeros();
    }

    /** Lo cobrado a los pasajeros cargados o, si todavía no se cargaron, precio por pasajero × cantidad. */
    public static BigDecimal subtotalVuelo(Reservation r) {
        if (!tieneVuelo(r)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (r.getDetalles() != null && !r.getDetalles().isEmpty()) {
            return r.getDetalles().stream()
                    .filter(d -> d.getPaymentStatus() != PaymentStatus.CANCELADO)
                    .map(d -> d.getPriceCharged() == null ? BigDecimal.ZERO : d.getPriceCharged())
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal porPasajero = r.getPrecioVueloPorPasajero() == null ? BigDecimal.ZERO : r.getPrecioVueloPorPasajero();
        int pasajeros = r.getCantidadPasajeros() == null ? 0 : r.getCantidadPasajeros();
        return porPasajero.multiply(BigDecimal.valueOf(pasajeros)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Parte de vuelo + estadías activas (spec 3.5). */
    public static BigDecimal montoTotal(Reservation r) {
        return estadiasActivas(r).stream()
                .map(EstadiaHotel::getPrecioTotal)
                .reduce(subtotalVuelo(r), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static int cantidadItems(Reservation r) {
        return (tieneVuelo(r) ? 1 : 0) + estadiasActivas(r).size();
    }

    public static boolean titularesCargados(Reservation r) {
        return estadiasActivas(r).stream().allMatch(e -> conTexto(e.getTitularNombre())
                && conTexto(e.getTitularDni()) && conTexto(e.getTitularTelefono()));
    }

    /** Pasajeros si hay vuelo y titulares si hay estadías. */
    public static boolean datosCompletos(Reservation r) {
        return cantidadItems(r) > 0 && (!tieneVuelo(r) || pasajerosCargados(r)) && titularesCargados(r);
    }

    public static long segundosRestantes(Reservation r, LocalDateTime ahora) {
        if (r.getLimiteTiempo() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(ahora, r.getLimiteTiempo()).toSeconds());
    }

    /** Estado de un carrito abierto según sus datos: listo para pagar o todavía incompleto (D11). */
    public static ReservationStatus estadoAbierto(Reservation r) {
        return datosCompletos(r) ? ReservationStatus.PENDIENTE_PAGO : ReservationStatus.INICIADA;
    }

    private static boolean conTexto(String s) {
        return s != null && !s.isBlank();
    }
}
