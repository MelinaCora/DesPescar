package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.EstadoItem;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CarritoCalculoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    static Reservation vacio() {
        return Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.INICIADA)
                .limiteTiempo(AHORA.plusMinutes(10)).build();
    }

    static void conVuelo(Reservation r, int pasajeros, String precioPorPasajero) {
        r.setFlightIds(new java.util.ArrayList<>(List.of(UUID.randomUUID())));
        r.setBaggageIds(new java.util.ArrayList<>(List.of(UUID.randomUUID())));
        r.setCantidadPasajeros(pasajeros);
        r.setPrecioVueloPorPasajero(new BigDecimal(precioPorPasajero));
    }

    static EstadiaHotel estadia(Reservation r, String precio, EstadoItem estado, String titular) {
        EstadiaHotel e = new EstadiaHotel();
        e.setReservation(r);
        e.setPrecioTotal(new BigDecimal(precio));
        e.setEstado(estado);
        e.setTitularNombre(titular);
        e.setTitularDni(titular == null ? null : "30111222");
        e.setTitularTelefono(titular == null ? null : "1155555555");
        r.getEstadias().add(e);
        return e;
    }

    static ReservationDetail pasajero(Reservation r, String precio, PaymentStatus estado) {
        ReservationDetail d = ReservationDetail.builder().reservation(r).priceCharged(new BigDecimal(precio))
                .paymentStatus(estado).outboundSeatNumber("1A").build();
        r.getDetalles().add(d);
        return d;
    }

    @Test
    void unCarritoVacioNoTieneItemsNiMonto() {
        Reservation r = vacio();
        assertEquals(0, CarritoCalculo.cantidadItems(r));
        assertEquals(new BigDecimal("0.00"), CarritoCalculo.montoTotal(r));
        assertFalse(CarritoCalculo.datosCompletos(r));
        assertEquals(ReservationStatus.INICIADA, CarritoCalculo.estadoAbierto(r));
    }

    @Test
    void sinPasajerosCargadosElVueloValePrecioPorCantidad() {
        Reservation r = vacio();
        conVuelo(r, 2, "240000");
        estadia(r, "580000.00", EstadoItem.ACTIVA, null);
        estadia(r, "100000.00", EstadoItem.CANCELADA, null);

        assertEquals(new BigDecimal("480000.00"), CarritoCalculo.subtotalVuelo(r));
        assertEquals(new BigDecimal("1060000.00"), CarritoCalculo.montoTotal(r));
        assertEquals(2, CarritoCalculo.cantidadItems(r));
        assertFalse(CarritoCalculo.pasajerosCargados(r));
    }

    @Test
    void conPasajerosSeSumaLoCobradoSinLosCancelados() {
        Reservation r = vacio();
        conVuelo(r, 2, "240000");
        pasajero(r, "240000.00", PaymentStatus.PENDIENTE);
        pasajero(r, "240000.00", PaymentStatus.PENDIENTE);
        assertEquals(new BigDecimal("480000.00"), CarritoCalculo.subtotalVuelo(r));
        assertTrue(CarritoCalculo.pasajerosCargados(r));

        r.getDetalles().get(1).setPaymentStatus(PaymentStatus.CANCELADO);
        assertEquals(new BigDecimal("240000.00"), CarritoCalculo.subtotalVuelo(r));
    }

    @Test
    void datosCompletosPideTitularesYPasajeros() {
        Reservation r = vacio();
        EstadiaHotel e = estadia(r, "580000.00", EstadoItem.ACTIVA, null);
        assertFalse(CarritoCalculo.datosCompletos(r));

        e.setTitularNombre("Ana Pérez");
        e.setTitularDni("30111222");
        e.setTitularTelefono("1155555555");
        assertTrue(CarritoCalculo.datosCompletos(r));
        assertEquals(ReservationStatus.PENDIENTE_PAGO, CarritoCalculo.estadoAbierto(r));

        conVuelo(r, 1, "100000");
        assertFalse(CarritoCalculo.datosCompletos(r));
        pasajero(r, "100000.00", PaymentStatus.PENDIENTE);
        assertTrue(CarritoCalculo.datosCompletos(r));
    }

    @Test
    void losSegundosRestantesNuncaSonNegativos() {
        Reservation r = vacio();
        assertEquals(600, CarritoCalculo.segundosRestantes(r, AHORA));
        assertEquals(0, CarritoCalculo.segundosRestantes(r, AHORA.plusMinutes(20)));
    }
}
