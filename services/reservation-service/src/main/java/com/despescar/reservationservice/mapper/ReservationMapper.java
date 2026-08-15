package com.despescar.reservationservice.mapper;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class ReservationMapper {


    private final ExtraBaggageMapper extraBaggageMapper;


    public ReservationResponse toResponse(Reservation reserva) {


        long segundosRestantes =
                Duration.between(
                        LocalDateTime.now(),
                        reserva.getLimiteTiempo()
                ).toSeconds();



        List<ReservationResponse.AsientoDetalleDTO> asientos =
                reserva.getDetalles()
                        .stream()
                        .map(this::mapDetalle)
                        .collect(Collectors.toList());



        return ReservationResponse.builder()
                .idCarrito(reserva.getId())
                .vueloCodigo(reserva.getVueloCodigo())
                .hotelId(reserva.getHotelId())
                .estadoGeneral(reserva.getEstado())
                .segundosRestantes(Math.max(0, segundosRestantes))
                .asientos(asientos)
                .build();
    }



    private ReservationResponse.AsientoDetalleDTO mapDetalle(
            ReservationDetail detalle
    ) {


        return ReservationResponse.AsientoDetalleDTO.builder()
                .numeroAsiento(detalle.getNumeroAsiento())
                .usuarioId(detalle.getUsuarioId())
                .pagadorId(detalle.getPagadorId())
                .precio(detalle.getPrecio())
                .estadoPago(detalle.getEstadoPago())
                .nombrePasajero(detalle.getNombrePasajero())
                .dniPasaporte(detalle.getDniPasaporte())
                .equipajes(
                        detalle.getEquipajes()
                                .stream()
                                .map(extraBaggageMapper::toResponse)
                                .collect(Collectors.toList())
                )
                .build();
    }

}