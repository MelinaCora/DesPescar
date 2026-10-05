package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.TramoPolitica;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cuánto se devuelve al cancelar: tramos de la política, zonas horarias y regla del vuelo. */
class ReembolsoCalculoTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final List<TramoPolitica> ESCALONADA = List.of(
            new TramoPolitica(24, 50), new TramoPolitica(72, 100));

    static EstadiaHotel estadia(String precio, LocalDate checkIn, String zona, List<TramoPolitica> politica) {
        EstadiaHotel e = new EstadiaHotel();
        e.setId(4L);
        e.setHotelNombre("Hotel Lago");
        e.setCheckIn(checkIn);
        e.setCheckOut(checkIn.plusDays(2));
        e.setHoraCheckIn(LocalTime.of(14, 0));
        e.setZonaHoraria(zona);
        e.setPrecioTotal(new BigDecimal(precio));
        e.setPoliticaCancelacion(new java.util.ArrayList<>(politica));
        e.setRetencionId(UUID.randomUUID());
        return e;
    }

    static Reservation reserva(LocalDateTime salida, EstadiaHotel... estadias) {
        Reservation r = Reservation.builder().id(15L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.CONFIRMADA)
                .limiteTiempo(LocalDateTime.of(2026, 10, 1, 10, 0)).build();
        if (salida != null) {
            r.getFlightIds().add(UUID.randomUUID());
            r.setCantidadPasajeros(2);
            r.setSalidaVuelo(salida);
            for (String asiento : List.of("1A", "1B")) {
                r.getDetalles().add(ReservationDetail.builder().reservation(r).outboundSeatNumber(asiento)
                        .priceCharged(new BigDecimal("100000.00")).paymentStatus(PaymentStatus.PAGADO).build());
            }
        }
        for (EstadiaHotel e : estadias) {
            e.setReservation(r);
            r.getEstadias().add(e);
        }
        return r;
    }

    @Test
    void usaElTramoMasExigenteQueTodaviaSeCumple() {
        assertEquals(100, ReembolsoCalculo.porcentaje(ESCALONADA, Duration.ofHours(72)));
        assertEquals(50, ReembolsoCalculo.porcentaje(ESCALONADA, Duration.ofHours(72).minusMinutes(1)));
        assertEquals(50, ReembolsoCalculo.porcentaje(ESCALONADA, Duration.ofHours(24)));
        assertEquals(0, ReembolsoCalculo.porcentaje(ESCALONADA, Duration.ofHours(23)));
        assertEquals(0, ReembolsoCalculo.porcentaje(List.of(), Duration.ofDays(30)));
        assertEquals(100, ReembolsoCalculo.porcentaje(List.of(new TramoPolitica(0, 100)), Duration.ofMinutes(5)));
    }

    @Test
    void lasHorasHastaElCheckInSeMidenEnLaZonaDelHotel() {
        // Check-in 10/11 14:00 en Madrid (UTC+1) = 13:00Z. A las 13:30Z del 7/11 faltan 71 h 30 min: 50 %.
        Reservation enMadrid = reserva(null, estadia("60000.00", LocalDate.of(2026, 11, 10), "Europe/Madrid", ESCALONADA));
        ReembolsoCalculo.Resultado r = ReembolsoCalculo.calcular(enMadrid, Instant.parse("2026-11-07T13:30:00Z"), ARGENTINA);
        assertEquals(50, r.items().get(0).porcentaje());
        assertEquals(new BigDecimal("30000.00"), r.total());

        // El mismo check-in en Buenos Aires (UTC-3) = 17:00Z: faltan 75 h 30 min: 100 %.
        Reservation enBuenosAires = reserva(null, estadia("60000.00", LocalDate.of(2026, 11, 10), ARGENTINA.getId(), ESCALONADA));
        r = ReembolsoCalculo.calcular(enBuenosAires, Instant.parse("2026-11-07T13:30:00Z"), ARGENTINA);
        assertEquals(100, r.items().get(0).porcentaje());
        assertEquals(new BigDecimal("60000.00"), r.total());
    }

    @Test
    void elVueloDevuelveTodoConMasDe24HorasYNadaDespues() {
        LocalDateTime salida = LocalDateTime.of(2026, 11, 10, 9, 0); // 12:00Z
        ReembolsoCalculo.Resultado antes = ReembolsoCalculo.calcular(reserva(salida), Instant.parse("2026-11-09T11:59:00Z"), ARGENTINA);
        assertEquals(ReembolsoCalculo.VUELO, antes.items().get(0).tipo());
        assertEquals(100, antes.items().get(0).porcentaje());
        assertEquals(new BigDecimal("200000.00"), antes.total());

        ReembolsoCalculo.Resultado tarde = ReembolsoCalculo.calcular(reserva(salida), Instant.parse("2026-11-09T12:00:00Z"), ARGENTINA);
        assertEquals(0, tarde.items().get(0).porcentaje());
        assertEquals(new BigDecimal("0.00"), tarde.total());
    }

    @Test
    void sumaVueloYEstadiasConCadaReglaYRedondeaADosDecimales() {
        Reservation r = reserva(LocalDateTime.of(2026, 11, 10, 9, 0),
                estadia("33333.33", LocalDate.of(2026, 11, 10), ARGENTINA.getId(), ESCALONADA));

        ReembolsoCalculo.Resultado res = ReembolsoCalculo.calcular(r, Instant.parse("2026-11-08T20:00:00Z"), ARGENTINA);

        assertEquals(2, res.items().size());
        assertEquals(new BigDecimal("200000.00"), res.items().get(0).monto());
        assertEquals(ReembolsoCalculo.ESTADIA, res.items().get(1).tipo());
        assertEquals(4L, res.items().get(1).estadiaId());
        assertEquals(50, res.items().get(1).porcentaje());
        assertEquals(new BigDecimal("16666.67"), res.items().get(1).monto());
        assertEquals(new BigDecimal("216666.67"), res.total());
        assertFalse(res.empezo());
    }

    @Test
    void empezoCuandoPasoLaPrimeraSalidaOElPrimerCheckIn() {
        Reservation r = reserva(LocalDateTime.of(2026, 11, 12, 9, 0),
                estadia("60000.00", LocalDate.of(2026, 11, 10), ARGENTINA.getId(), ESCALONADA));
        assertFalse(ReembolsoCalculo.calcular(r, Instant.parse("2026-11-10T16:59:00Z"), ARGENTINA).empezo());
        assertTrue(ReembolsoCalculo.calcular(r, Instant.parse("2026-11-10T17:00:00Z"), ARGENTINA).empezo());
    }
}
