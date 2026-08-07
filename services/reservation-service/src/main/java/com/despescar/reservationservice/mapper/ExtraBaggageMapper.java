package com.despescar.reservationservice.mapper;

import com.despescar.reservationservice.dto.extraBaggage.response.ExtraBaggageResponse;
import com.despescar.reservationservice.entity.ExtraBaggage;
import org.springframework.stereotype.Component;

@Component
public class ExtraBaggageMapper {


    public ExtraBaggageResponse toResponse(
            ExtraBaggage baggage
    ){

        ExtraBaggageResponse response =
                new ExtraBaggageResponse();

        response.setId(baggage.getId());

        response.setDetalleReservaId(
                baggage.getDetalleReserva().getId()
        );

        response.setPeso(
                baggage.getPeso()
        );

        response.setPrecio(
                baggage.getPrecio()
        );


        return response;
    }
}