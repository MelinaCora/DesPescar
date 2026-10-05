package com.despescar.hotelservice.dto.response;

import com.despescar.hotelservice.entity.Servicio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Card de resultados. disponible y precioTotalDesde son null si la búsqueda no trae fechas. */
public record HotelResumenResponse(
        UUID id, String nombre, String ciudad, String pais, int estrellas, String imagenPrincipal,
        List<Servicio> servicios, boolean allInclusive, BigDecimal precioDesde,
        double calificacionPromedio, int cantidadResenas, Boolean disponible, BigDecimal precioTotalDesde) {
}
