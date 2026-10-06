package com.despescar.reservationservice.mapper;

import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.service.CarritoCalculo;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationMapper {

    private final ReservationDetailMapper detailMapper;
    private final Clock clock;

    public ReservationResponse toResponse(Reservation reserva) {
        boolean conVuelo = CarritoCalculo.tieneVuelo(reserva);
        return ReservationResponse.builder()
                .idCarrito(reserva.getId())
                .creadorId(reserva.getCreadorId())
                .packageId(reserva.getPackageId())
                .vueloCodigo(conVuelo ? reserva.getFlightIds().get(0).toString() : null)
                .hotelId(reserva.getHotelId())
                .estadoGeneral(reserva.getEstado())
                .segundosRestantes(CarritoCalculo.segundosRestantes(reserva, LocalDateTime.now(clock)))
                .montoTotal(CarritoCalculo.montoTotal(reserva))
                .moneda(CarritoCalculo.MONEDA)
                .cantidadItems(CarritoCalculo.cantidadItems(reserva))
                .datosCompletos(CarritoCalculo.datosCompletos(reserva))
                .vuelo(conVuelo ? vuelo(reserva) : null)
                .estadias(lista(reserva.getEstadias()).stream().map(this::estadia).toList())
                .asientos(lista(reserva.getDetalles()).stream().map(detailMapper::toResponse).toList())
                .contactoEmail(reserva.getContactoEmail())
                .contactoTelefono(reserva.getContactoTelefono())
                .creadoEn(reserva.getCreadoEn())
                .motivoCancelacion(reserva.getMotivoCancelacion())
                .canceladaEn(reserva.getCanceladaEn())
                .montoReembolsado(reserva.getMontoReembolsado())
                .reembolsoPendiente(reserva.isReembolsoPendiente())
                .pagoEnGrupo(reserva.getTipoPago() == PaymentType.SPLIT_PAYMENT)
                .build();
    }

    private ReservationResponse.VueloCarritoDTO vuelo(Reservation r) {
        return ReservationResponse.VueloCarritoDTO.builder()
                .flightIds(new ArrayList<>(r.getFlightIds()))
                .fareIds(new ArrayList<>(lista(r.getBaggageIds())))
                .cantidadPasajeros(r.getCantidadPasajeros())
                .precioPorPasajero(r.getPrecioVueloPorPasajero())
                .subtotal(CarritoCalculo.subtotalVuelo(r))
                .salida(r.getSalidaVuelo())
                .tarifas(r.getTarifasVuelo())
                .pasajerosCargados(CarritoCalculo.pasajerosCargados(r))
                .build();
    }

    private ReservationResponse.EstadiaDTO estadia(EstadiaHotel e) {
        return ReservationResponse.EstadiaDTO.builder()
                .id(e.getId())
                .hotelId(e.getHotelId())
                .hotelNombre(e.getHotelNombre())
                .ciudad(e.getCiudad())
                .tipoHabitacionId(e.getTipoHabitacionId())
                .tipoHabitacionNombre(e.getTipoHabitacionNombre())
                .checkIn(e.getCheckIn())
                .checkOut(e.getCheckOut())
                .noches(ChronoUnit.DAYS.between(e.getCheckIn(), e.getCheckOut()))
                .cantidadHabitaciones(e.getCantidadHabitaciones())
                .huespedes(e.getHuespedes())
                .precioTotal(e.getPrecioTotal())
                .moneda(e.getMoneda())
                .horaCheckIn(e.getHoraCheckIn())
                .zonaHoraria(e.getZonaHoraria())
                .politicaCancelacion(lista(e.getPoliticaCancelacion()).stream()
                        .map(t -> new ReservationResponse.TramoDTO(t.getHorasAntes(), t.getPorcentajeReembolso()))
                        .toList())
                .titularNombre(e.getTitularNombre())
                .titularDni(e.getTitularDni())
                .titularTelefono(e.getTitularTelefono())
                .estado(e.getEstado())
                .build();
    }

    private static <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }
}
