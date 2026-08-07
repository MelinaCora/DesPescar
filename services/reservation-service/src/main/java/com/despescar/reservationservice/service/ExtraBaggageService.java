package com.despescar.reservationservice.service;


import com.despescar.reservationservice.entity.ExtraBaggage;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.ExtraBaggageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ExtraBaggageService {


    private final ExtraBaggageRepository extraBaggageRepository;
    private final BookingDetailRepository bookingDetailRepository;



    public ExtraBaggage addBaggage(Long detalleReservaId, Double peso){


        ReservationDetail detalle = bookingDetailRepository
                .findById(detalleReservaId)
                .orElseThrow(() ->
                        new RuntimeException("Detalle de reserva no encontrado")
                );


        ExtraBaggage baggage = ExtraBaggage.builder()
                .detalleReserva(detalle)
                .peso(peso)
                .precio(calcularPrecio(peso))
                .build();


        return extraBaggageRepository.save(baggage);
    }



    private BigDecimal calcularPrecio(Double peso){

        if(peso <= 15)
            return BigDecimal.valueOf(30.0);

        if(peso <= 23)
            return BigDecimal.valueOf(50.0);


        return BigDecimal.valueOf(80.0);
    }

}
