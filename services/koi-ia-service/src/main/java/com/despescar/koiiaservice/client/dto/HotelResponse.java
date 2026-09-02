package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelResponse {
    private UUID id;
    private String nombre;
    private String ciudad;
    private String direccion;
    private int estrellas;
    private double precioPorNoche;
    private int habitacionesDisponibles;
    private Boolean allInclusive;
}
