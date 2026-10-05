package com.despescar.hotelservice.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Los campos de cotización son null si el detalle se pidió sin fechas. */
public record HabitacionDetalleResponse(
        UUID id, String nombre, String descripcion, int capacidad, BigDecimal precioPorNoche,
        List<String> imagenes, Integer unidadesLibres, Integer habitacionesNecesarias,
        BigDecimal precioTotal, Boolean disponible) {
}
