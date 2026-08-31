package com.despescar.hotelservice.service;

import com.despescar.hotelservice.dto.hotel.request.HotelRequest;
import com.despescar.hotelservice.dto.hotel.response.HotelResponse;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.exception.HotelNotFoundException;
import com.despescar.hotelservice.mapper.HotelMapper;
import com.despescar.hotelservice.repository.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotelService  {


    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;




    public HotelResponse createHotel(HotelRequest request) {
        Hotel hotel = hotelMapper.toEntity(request);
        Hotel savedHotel = hotelRepository.save(hotel);
        return hotelMapper.toResponse(savedHotel);
    }


    public List<HotelResponse> getAllHotels() {
        return hotelRepository.findAll()
                .stream()
                .map(hotelMapper::toResponse)
                .collect(Collectors.toList());
    }


    public HotelResponse getHotelById(UUID id) {
        // Aquí eventualmente usarán su propia excepción personalizada (ej. HotelNotFoundException)
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));
        return hotelMapper.toResponse(hotel);
    }


    public List<HotelResponse> getHotelsByCity(String city) {
        return hotelRepository.findByCiudadIgnoreCase(city)
                .stream()
                .map(hotelMapper::toResponse)
                .collect(Collectors.toList());
    }


    public HotelResponse updateHotel(UUID id, HotelRequest request) {
        Hotel existingHotel = hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));

        existingHotel.setNombre(request.getNombre());
        existingHotel.setCiudad(request.getCiudad());
        existingHotel.setDireccion(request.getDireccion());
        existingHotel.setEstrellas(request.getEstrellas());
        existingHotel.setPrecioPorNoche(request.getPrecioPorNoche());
        existingHotel.setHabitacionesDisponibles(request.getHabitacionesDisponibles());
        existingHotel.setAllInclusive(request.getAllInclusive());

        Hotel updatedHotel = hotelRepository.save(existingHotel);
        return hotelMapper.toResponse(updatedHotel);
    }


    public void deleteHotel(UUID id) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));
        hotelRepository.delete(hotel);
    }


}