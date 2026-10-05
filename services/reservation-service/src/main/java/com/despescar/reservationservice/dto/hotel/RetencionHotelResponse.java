package com.despescar.reservationservice.dto.hotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Retención devuelta por hotel-service (contrato C1): lo que el carrito copia en la estadía. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetencionHotelResponse {
    private UUID retencionId;
    private UUID hotelId;
    private String hotelNombre;
    private String ciudad;
    private UUID tipoHabitacionId;
    private String tipoHabitacionNombre;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private long noches;
    private int cantidad;
    private int huespedes;
    private BigDecimal precioTotal;
    private String moneda;
    private LocalTime horaCheckIn;
    private String zonaHoraria;
    private List<TramoHotelDto> politicaCancelacion = new ArrayList<>();
    private String estado;
    private Instant expiraEn;
}
