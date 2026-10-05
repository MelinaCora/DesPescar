package com.despescar.hotelservice.mapper;

import com.despescar.hotelservice.domain.Cotizacion;
import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.dto.request.HabitacionRequest;
import com.despescar.hotelservice.dto.request.HotelRequest;
import com.despescar.hotelservice.dto.response.HabitacionDetalleResponse;
import com.despescar.hotelservice.dto.response.HotelDetalleResponse;
import com.despescar.hotelservice.dto.response.HotelResumenResponse;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.entity.TramoCancelacion;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class HotelMapper {

    public Hotel toEntity(HotelRequest request) {
        Hotel hotel = new Hotel();
        hotel.setNombre(request.nombre().trim());
        hotel.setCiudad(request.ciudad().trim());
        hotel.setPais(request.pais().trim());
        hotel.setDireccion(request.direccion().trim());
        hotel.setEstrellas(request.estrellas());
        hotel.setDescripcion(request.descripcion());
        hotel.setAllInclusive(request.allInclusive());
        hotel.setImagenes(new ArrayList<>(request.imagenes()));
        hotel.setServicios(new HashSet<>(request.servicios()));
        hotel.setPoliticaCancelacion(new ArrayList<>(request.politicaCancelacion().stream()
                .map(t -> new TramoCancelacion(t.horasAntes(), t.porcentajeReembolso()))
                .toList()));
        if (request.horaCheckIn() != null) {
            hotel.setHoraCheckIn(request.horaCheckIn());
        }
        if (request.zonaHoraria() != null && !request.zonaHoraria().isBlank()) {
            hotel.setZonaHoraria(validarZona(request.zonaHoraria().trim()));
        }
        hotel.setAdminUserId(request.adminUserId());
        for (HabitacionRequest h : request.habitaciones()) {
            hotel.agregarHabitacion(toEntity(h));
        }
        return hotel;
    }

    private TipoHabitacion toEntity(HabitacionRequest request) {
        TipoHabitacion tipo = new TipoHabitacion();
        tipo.setNombre(request.nombre().trim());
        tipo.setDescripcion(request.descripcion());
        tipo.setCapacidad(request.capacidad());
        tipo.setPrecioPorNoche(request.precioPorNoche());
        tipo.setCantidadUnidades(request.cantidadUnidades());
        tipo.setImagenes(new ArrayList<>(request.imagenes()));
        return tipo;
    }

    private String validarZona(String zona) {
        try {
            return ZoneId.of(zona).getId();
        } catch (DateTimeException e) {
            throw new SolicitudInvalidaException("Zona horaria desconocida: " + zona);
        }
    }

    public HotelResumenResponse toResumen(Hotel hotel, BigDecimal precioDesde, Boolean disponible,
                                          BigDecimal precioTotalDesde) {
        String imagenPrincipal = hotel.getImagenes().isEmpty() ? null : hotel.getImagenes().get(0);
        return new HotelResumenResponse(hotel.getId(), hotel.getNombre(), hotel.getCiudad(), hotel.getPais(),
                hotel.getEstrellas(), imagenPrincipal, hotel.getServicios().stream().sorted().toList(), hotel.isAllInclusive(),
                precioDesde, hotel.getCalificacionPromedio(), hotel.getCantidadResenas(), disponible,
                precioTotalDesde);
    }

    /** cotizacion es null cuando el detalle se pide sin fechas. */
    public HabitacionDetalleResponse toHabitacion(TipoHabitacion tipo, Cotizacion cotizacion) {
        return new HabitacionDetalleResponse(tipo.getId(), tipo.getNombre(), tipo.getDescripcion(),
                tipo.getCapacidad(), tipo.getPrecioPorNoche(), List.copyOf(tipo.getImagenes()),
                cotizacion == null ? null : cotizacion.unidadesLibres(),
                cotizacion == null ? null : cotizacion.habitacionesNecesarias(),
                cotizacion == null ? null : cotizacion.precioTotal(),
                cotizacion == null ? null : cotizacion.disponible());
    }

    public HotelDetalleResponse toDetalle(Hotel hotel, Long noches, List<HabitacionDetalleResponse> habitaciones) {
        List<TramoDto> politica = hotel.getPoliticaCancelacion().stream()
                .map(t -> new TramoDto(t.getHorasAntes(), t.getPorcentajeReembolso()))
                .toList();
        return new HotelDetalleResponse(hotel.getId(), hotel.getNombre(), hotel.getCiudad(), hotel.getPais(),
                hotel.getDireccion(), hotel.getEstrellas(), hotel.getDescripcion(), List.copyOf(hotel.getImagenes()),
                hotel.getServicios().stream().sorted().toList(), hotel.isAllInclusive(), hotel.getHoraCheckIn(),
                hotel.getZonaHoraria(), politica, hotel.getCalificacionPromedio(), hotel.getCantidadResenas(),
                noches, habitaciones);
    }
}
