package com.despescar.reservationservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.ReservationStatus;

class ReservationMapperTest {

    private final ReservationMapper reservationMapper = new ReservationMapper(new ReservationDetailMapper());

    @Test
    void toResponseShouldExposeTotalAmountAndSharedCurrency() {
        Reservation reservation = reservation("ARS", "ARS");

        var response = reservationMapper.toResponse(reservation);

        assertThat(response.getIdCarrito()).isEqualTo(77L);
        assertThat(response.getHotelId()).isEqualTo(reservation.getHotelId());
        assertThat(response.getMontoTotal()).isEqualByComparingTo("1250.50");
        assertThat(response.getMoneda()).isEqualTo("ARS");
        assertThat(response.getAsientos()).hasSize(2);
    }

    @Test
    void toResponseShouldLeaveCurrencyNullWhenReservationMixesCurrencies() {
        Reservation reservation = reservation("ARS", "USD");

        var response = reservationMapper.toResponse(reservation);

        assertThat(response.getMontoTotal()).isEqualByComparingTo("1250.50");
        assertThat(response.getMoneda()).isNull();
    }

    private Reservation reservation(String firstCurrency, String secondCurrency) {
        Reservation reservation = Reservation.builder()
                .id(77L)
                .flightIds(List.of(UUID.randomUUID()))
                .hotelId(UUID.randomUUID())
                .estado(ReservationStatus.PENDIENTE_PAGO)
                .limiteTiempo(LocalDateTime.now().plusMinutes(10))
                .build();

        ReservationDetail firstSeat = ReservationDetail.builder()
                .reservation(reservation)
                .outboundSeatNumber("12A")
                .payerUserId(55L)
                .priceCharged(new BigDecimal("1000.00"))
                .fareCurrency(firstCurrency)
                .paymentStatus(PaymentStatus.PENDIENTE)
                .build();

        ReservationDetail secondSeat = ReservationDetail.builder()
                .reservation(reservation)
                .outboundSeatNumber("12B")
                .payerUserId(55L)
                .priceCharged(new BigDecimal("250.50"))
                .fareCurrency(secondCurrency)
                .paymentStatus(PaymentStatus.PENDIENTE)
                .build();

        reservation.setDetalles(List.of(firstSeat, secondSeat));
        return reservation;
    }
}
