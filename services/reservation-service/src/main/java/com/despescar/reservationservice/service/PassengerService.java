package com.despescar.reservationservice.service;

import com.despescar.reservationservice.dto.passengers.request.PassengerRequest;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class PassengerService {


    private final BookingDetailRepository detailRepository;


    @Transactional
    public void updatePassengerData(
            Long reservationId,
            PassengerRequest dto
    ) {


        ReservationDetail detail =
                detailRepository.findByReservaIdAndUsuarioId(
                        reservationId,
                        dto.getUsuarioId()
                );


        if(detail == null){

            throw new BookingException(
                    "ASIENTO_NO_ASIGNADO",
                    "El usuario no tiene asiento asignado.",
                    HttpStatus.BAD_REQUEST
            );
        }


        detail.setNombrePasajero(
                dto.getNombrePasajero()
        );


        detail.setDniPasaporte(
                dto.getDniPasaporte()
        );


        detailRepository.save(detail);
    }

}