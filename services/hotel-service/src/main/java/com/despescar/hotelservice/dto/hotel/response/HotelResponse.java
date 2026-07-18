package com.despescar.hotelservice.dto.hotel.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor

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
