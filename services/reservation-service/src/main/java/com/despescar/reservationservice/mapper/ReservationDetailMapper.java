package com.despescar.reservationservice.mapper;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.ReservationDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
// import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReservationDetailMapper {

    // Si pasaste al modelo de Tarifas y borraste ExtraBaggage, elimina este mapper:
    // private final ExtraBaggageMapper extraBaggageMapper;

    public ReservationResponse.AsientoDetalleDTO toResponse(ReservationDetail detalle) {

        return ReservationResponse.AsientoDetalleDTO.builder()
                .asientoIda(detalle.getOutboundSeatNumber())
                .asientoVuelta(detalle.getReturnSeatNumber())

                .pagadorId(detalle.getPayerUserId())
                .precioCobrado(detalle.getPriceCharged())

                // Convertimos el Enum a String (o puedes usar el Enum directamente si el DTO lo soporta)
                .estadoPago(detalle.getPaymentStatus() != null ? detalle.getPaymentStatus().name() : null)

                .nombrePasajero(detalle.getPassengerName())
                .dniPasaporte(detalle.getPassengerDni())

                .tarifaNombre(detalle.getFareName())

                // Si conservaste los equipajes extra aparte de la tarifa, descomenta esto:
                /*
                .equipajes(
                        detalle.getEquipajes() != null ?
                        detalle.getEquipajes().stream()
                                .map(extraBaggageMapper::toResponse)
                                .collect(Collectors.toList())
                        : new ArrayList<>()
                )
                */
                .build();
    }
}