package com.despescar.reservationservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.TramoPolitica;
import com.despescar.reservationservice.enums.EstadoItem;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationMapperTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    // 15:00 en Buenos Aires
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    private final ReservationMapper mapper = new ReservationMapper(new ReservationDetailMapper(), RELOJ);

    private Reservation carrito() {
        return Reservation.builder().id(77L).creadorId(55L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.INICIADA)
                .limiteTiempo(AHORA.plusMinutes(10)).build();
    }

    @Test
    void unCarritoConVueloCargadoInformaTotalYPasajeros() {
        Reservation r = carrito();
        UUID ida = UUID.randomUUID();
        r.setFlightIds(new ArrayList<>(List.of(ida)));
        r.setBaggageIds(new ArrayList<>(List.of(UUID.randomUUID())));
        r.setCantidadPasajeros(2);
        r.setPrecioVueloPorPasajero(new BigDecimal("625.25"));
        r.setTarifasVuelo("Light");
        r.setSalidaVuelo(LocalDateTime.of(2026, 10, 19, 8, 0));
        r.getDetalles().add(ReservationDetail.builder().reservation(r).outboundSeatNumber("12A")
                .payerUserId(55L).priceCharged(new BigDecimal("625.25")).paymentStatus(PaymentStatus.PENDIENTE).build());
        r.getDetalles().add(ReservationDetail.builder().reservation(r).outboundSeatNumber("12B")
                .payerUserId(55L).priceCharged(new BigDecimal("625.25")).paymentStatus(PaymentStatus.PENDIENTE).build());

        ReservationResponse response = mapper.toResponse(r);

        assertThat(response.getIdCarrito()).isEqualTo(77L);
        assertThat(response.getCreadorId()).isEqualTo(55L);
        assertThat(response.getMontoTotal()).isEqualByComparingTo("1250.50");
        assertThat(response.getMoneda()).isEqualTo("ARS");
        assertThat(response.getSegundosRestantes()).isEqualTo(600L);
        assertThat(response.getCantidadItems()).isEqualTo(1);
        assertThat(response.getVueloCodigo()).isEqualTo(ida.toString());
        assertThat(response.getVuelo().getPasajerosCargados()).isTrue();
        assertThat(response.getVuelo().getSubtotal()).isEqualByComparingTo("1250.50");
        assertThat(response.getVuelo().getTarifas()).isEqualTo("Light");
        assertThat(response.getAsientos()).hasSize(2);
        assertThat(response.getEstadias()).isEmpty();
    }

    @Test
    void unCarritoSoloDeHotelNoTieneVuelo() {
        Reservation r = carrito();
        EstadiaHotel e = new EstadiaHotel();
        e.setId(3L);
        e.setReservation(r);
        e.setHotelId(UUID.randomUUID());
        e.setHotelNombre("Sheraton Córdoba");
        e.setCiudad("Córdoba");
        e.setTipoHabitacionId(UUID.randomUUID());
        e.setTipoHabitacionNombre("Doble");
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setCantidadHabitaciones(2);
        e.setHuespedes(3);
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(new BigDecimal("580000.00"));
        e.setMoneda("ARS");
        e.setHoraCheckIn(LocalTime.of(14, 0));
        e.setZonaHoraria("America/Argentina/Buenos_Aires");
        e.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoPolitica(48, 100))));
        r.getEstadias().add(e);

        ReservationResponse response = mapper.toResponse(r);

        assertThat(response.getVuelo()).isNull();
        assertThat(response.getVueloCodigo()).isNull();
        assertThat(response.getMontoTotal()).isEqualByComparingTo("580000.00");
        assertThat(response.getDatosCompletos()).isFalse();
        assertThat(response.getEstadias()).hasSize(1);
        ReservationResponse.EstadiaDTO dto = response.getEstadias().get(0);
        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getNoches()).isEqualTo(2L);
        assertThat(dto.getEstado()).isEqualTo(EstadoItem.ACTIVA);
        assertThat(dto.getPoliticaCancelacion()).extracting(ReservationResponse.TramoDTO::getHorasAntes).containsExactly(48);
    }
}
