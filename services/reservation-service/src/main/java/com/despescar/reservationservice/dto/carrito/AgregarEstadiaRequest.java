package com.despescar.reservationservice.dto.carrito;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * POST /carrito/estadias (contrato C4). Las fechas y la capacidad las valida hotel-service al
 * retener (400 SOLICITUD_HOTEL_INVALIDA con su mensaje).
 */
public record AgregarEstadiaRequest(
        @NotNull UUID hotelId,
        @NotNull UUID tipoHabitacionId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Min(1) @Max(10) Integer cantidadHabitaciones,
        @NotNull @Min(1) @Max(10) Integer huespedes) {
}
