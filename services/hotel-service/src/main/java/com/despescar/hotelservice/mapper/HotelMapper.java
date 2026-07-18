package com.despescar.hotelservice.mapper;

import com.despescar.hotelservice.dto.hotel.request.HotelRequest;
import com.despescar.hotelservice.dto.hotel.response.HotelResponse;
import com.despescar.hotelservice.entity.Hotel;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class HotelMapper {

    public Hotel toEntity(HotelRequest request) {
        if (request == null) {
            return null;
        }
        Hotel hotel = new Hotel();
        hotel.setNombre(request.getNombre());
        hotel.setCiudad(request.getCiudad());
        hotel.setDireccion(request.getDireccion());
        hotel.setEstrellas(request.getEstrellas());
        hotel.setPrecioPorNoche(request.getPrecioPorNoche());
        hotel.setHabitacionesDisponibles(request.getHabitacionesDisponibles());
        hotel.setAllInclusive(request.getAllInclusive());
        return hotel;
    }

    public HotelResponse toResponse(Hotel hotel) {
        if (hotel == null) {
            return null;
        }

        HotelResponse response = new HotelResponse();
        response.setId(hotel.getId());
        response.setCiudad(hotel.getCiudad());
        response.setDireccion(hotel.getDireccion());
        response.setEstrellas(hotel.getEstrellas());
        response.setPrecioPorNoche(hotel.getPrecioPorNoche());
        response.setHabitacionesDisponibles(hotel.getHabitacionesDisponibles());
        response.setAllInclusive(hotel.getAllInclusive());
        return response;
    }

}
