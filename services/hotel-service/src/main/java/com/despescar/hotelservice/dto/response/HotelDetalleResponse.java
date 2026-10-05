package com.despescar.hotelservice.dto.response;

import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.entity.Servicio;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record HotelDetalleResponse(
        UUID id, String nombre, String ciudad, String pais, String direccion, int estrellas,
        String descripcion, List<String> imagenes, List<Servicio> servicios, boolean allInclusive,
        LocalTime horaCheckIn, String zonaHoraria, List<TramoDto> politicaCancelacion,
        double calificacionPromedio, int cantidadResenas, Long noches,
        List<HabitacionDetalleResponse> habitaciones) {
}
