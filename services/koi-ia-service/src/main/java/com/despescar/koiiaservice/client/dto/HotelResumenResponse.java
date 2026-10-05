package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

/** Lo que KOI usa de GET /hoteles (hotel-service, público). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelResumenResponse(UUID id, String nombre, String ciudad, int estrellas, String imagenPrincipal,
                                   Boolean disponible, BigDecimal precioTotalDesde) {
}
