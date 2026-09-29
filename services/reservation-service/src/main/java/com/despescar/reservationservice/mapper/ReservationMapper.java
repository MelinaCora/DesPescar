package com.despescar.reservationservice.mapper;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReservationMapper {

    // 1. Inyectamos el mapper específico de los detalles que ya arreglamos
    private final ReservationDetailMapper detailMapper;

    public ReservationResponse toResponse(Reservation reserva) {

        long segundosRestantes = 0;
        if (reserva.getLimiteTiempo() != null) {
            segundosRestantes = Duration.between(
                    LocalDateTime.now(),
                    reserva.getLimiteTiempo()
            ).toSeconds();
        }

        // 2. Delegamos el mapeo de la lista a detailMapper
        List<ReservationResponse.AsientoDetalleDTO> asientos = reserva.getDetalles()
                .stream()
                .map(detailMapper::toResponse)
                .collect(Collectors.toList());

        BigDecimal montoTotal = reserva.getDetalles()
                .stream()
                .map(detail -> detail.getPriceCharged() == null ? BigDecimal.ZERO : detail.getPriceCharged())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> monedas = reserva.getDetalles()
                .stream()
                .map(detail -> detail.getFareCurrency() == null ? null : detail.getFareCurrency().trim().toUpperCase())
                .filter(Objects::nonNull)
                .filter(currency -> !currency.isBlank())
                .distinct()
                .toList();

        return ReservationResponse.builder()
                .idCarrito(reserva.getId())
                .packageId(reserva.getPackageId())
                // 3. Tomamos el primer vuelo de la lista para no romper la compatibilidad con tu frontend
                .vueloCodigo(reserva.getFlightIds() != null && !reserva.getFlightIds().isEmpty() ?
                        reserva.getFlightIds().get(0).toString() : null)
                .hotelId(reserva.getHotelId())
                .estadoGeneral(reserva.getEstado())
                .segundosRestantes(Math.max(0, segundosRestantes))
                .montoTotal(montoTotal)
                .moneda(monedas.size() == 1 ? monedas.get(0) : null)
                .asientos(asientos)
                .build();
    }
}