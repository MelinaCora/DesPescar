package com.despescar.reservationservice.mapper;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.ReservationDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class ReservationDetailMapper {


    private final ExtraBaggageMapper extraBaggageMapper;



    public ReservationResponse.AsientoDetalleDTO toResponse(
            ReservationDetail detalle
    ) {


        return ReservationResponse.AsientoDetalleDTO.builder()

                .numeroAsiento(
                        detalle.getNumeroAsiento()
                )

                .usuarioId(
                        detalle.getUsuarioId()
                )

                .pagadorId(
                        detalle.getPagadorId()
                )

                .precio(
                        detalle.getPrecio()
                )

                .estadoPago(
                        detalle.getEstadoPago()
                )

                .nombrePasajero(
                        detalle.getNombrePasajero()
                )

                .dniPasaporte(
                        detalle.getDniPasaporte()
                )

                .equipajes(
                        detalle.getEquipajes()
                                .stream()
                                .map(extraBaggageMapper::toResponse)
                                .collect(Collectors.toList())
                )

                .build();
    }
}