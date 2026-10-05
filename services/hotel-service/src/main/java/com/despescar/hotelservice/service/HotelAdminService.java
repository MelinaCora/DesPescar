package com.despescar.hotelservice.service;

import com.despescar.hotelservice.domain.PoliticaCancelacionValidator;
import com.despescar.hotelservice.dto.request.HotelRequest;
import com.despescar.hotelservice.dto.response.HotelDetalleResponse;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.mapper.HotelMapper;
import com.despescar.hotelservice.repository.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta de hoteles. La edición y la verificación de propiedad llegan en la etapa de admin. */
@Service
public class HotelAdminService {

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;

    public HotelAdminService(HotelRepository hotelRepository, HotelMapper hotelMapper) {
        this.hotelRepository = hotelRepository;
        this.hotelMapper = hotelMapper;
    }

    @Transactional
    public HotelDetalleResponse crear(HotelRequest request) {
        Hotel hotel = hotelMapper.toEntity(request);
        PoliticaCancelacionValidator.validar(hotel.getPoliticaCancelacion());
        Hotel guardado = hotelRepository.save(hotel);
        return hotelMapper.toDetalle(guardado, null, guardado.getHabitaciones().stream()
                .filter(TipoHabitacion::isActivo)
                .map(t -> hotelMapper.toHabitacion(t, null))
                .toList());
    }
}
