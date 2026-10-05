package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Lo que KOI usa de GET /hoteles/{id}?checkIn&checkOut&huespedes (hotel-service, público). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelDetalleResponse(UUID id, String nombre, String ciudad, int estrellas, List<String> imagenes,
                                   Long noches, List<Habitacion> habitaciones) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Habitacion(UUID id, String nombre, int capacidad, BigDecimal precioPorNoche,
                             Integer unidadesLibres, Boolean disponible) {
    }
}
