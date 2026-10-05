package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Un destino con hoteles activos, de GET /hoteles/destinos. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DestinoResponse(String ciudad, String pais) {
}
