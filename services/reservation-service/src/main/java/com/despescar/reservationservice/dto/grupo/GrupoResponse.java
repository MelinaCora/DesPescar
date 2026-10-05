package com.despescar.reservationservice.dto.grupo;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un pago en grupo visto por un usuario (CB2). No lleva datos personales de nadie (D-b18): ni el id
 * del organizador, ni pasajeros, titulares, teléfonos o asientos. enlaceToken solo para quien tiene parte.
 */
public record GrupoResponse(
        Long reservaId,
        EstadoGrupo estado,
        LocalDateTime venceEn,
        long segundosRestantes,
        BigDecimal montoTotal,
        String moneda,
        BigDecimal montoPagado,
        int cantidadPartes,
        long partesPagadas,
        boolean soyOrganizador,
        Integer miParte,
        String enlaceToken,
        boolean puedeEditarMontos,
        String motivoCierre,
        List<ParteDTO> partes,
        ViajeDTO viaje) {

    public record ParteDTO(int numero, BigDecimal monto, EstadoParte estado, String apodo,
                           boolean esOrganizador, boolean esMia) {
    }

    public record ViajeDTO(VueloDTO vuelo, List<EstadiaDTO> estadias) {
    }

    /** Las ciudades y horarios los lee el front con GET /api/flights/{id} (D28 de E2). */
    public record VueloDTO(List<UUID> flightIds, Integer cantidadPasajeros, LocalDateTime salida, String tarifas) {
    }

    public record EstadiaDTO(String hotelNombre, String ciudad, String tipoHabitacionNombre, LocalDate checkIn,
                             LocalDate checkOut, long noches, int cantidadHabitaciones, int huespedes,
                             List<ReservationResponse.TramoDTO> politicaCancelacion) {
    }
}
