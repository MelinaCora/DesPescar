package com.despescar.reservationservice.mapper;

import com.despescar.reservationservice.dto.grupo.GrupoResponse;
import com.despescar.reservationservice.dto.grupo.GrupoResumenResponse;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.service.CarritoCalculo;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Arma la vista de un grupo para un usuario, sin datos personales (D-b18). */
@Component
public class GrupoMapper {

    private final Clock clock;

    public GrupoMapper(Clock clock) {
        this.clock = clock;
    }

    public GrupoResponse toResponse(GrupoPago grupo, Long usuarioId) {
        Reservation reserva = grupo.getReservation();
        Optional<ParteGrupo> mia = grupo.parteDe(usuarioId);
        boolean organizador = Objects.equals(grupo.getOrganizadorId(), usuarioId);
        long segundos = segundosRestantes(grupo);
        boolean editable = organizador && grupo.getEstado() == EstadoGrupo.ABIERTO && !grupo.hayPagadas() && segundos > 0;
        List<GrupoResponse.ParteDTO> partes = grupo.getPartes().stream()
                .map(p -> new GrupoResponse.ParteDTO(p.getNumero(), p.getMonto(), p.getEstado(), p.getApodo(),
                        Objects.equals(p.getUsuarioId(), grupo.getOrganizadorId()) && p.getNumero() == 1,
                        usuarioId != null && usuarioId.equals(p.getUsuarioId())))
                .toList();
        return new GrupoResponse(
                reserva.getId(),
                grupo.getEstado(),
                grupo.getVenceEn(),
                segundos,
                CarritoCalculo.montoTotal(reserva),
                CarritoCalculo.MONEDA,
                grupo.montoPagado(),
                grupo.getPartes().size(),
                grupo.cantidadPagadas(),
                organizador,
                mia.map(ParteGrupo::getNumero).orElse(null),
                mia.isPresent() ? grupo.getTokenEnlace() : null,
                editable,
                grupo.getMotivoCierre(),
                partes,
                viaje(reserva));
    }

    public GrupoResumenResponse resumen(GrupoPago grupo, Long usuarioId) {
        ParteGrupo mia = grupo.parteDe(usuarioId).orElseThrow();
        String destino = CarritoCalculo.estadiasActivas(grupo.getReservation()).stream()
                .map(EstadiaHotel::getCiudad).filter(Objects::nonNull).findFirst().orElse(null);
        return new GrupoResumenResponse(
                grupo.getReservation().getId(),
                grupo.getTokenEnlace(),
                grupo.getEstado(),
                grupo.getVenceEn(),
                segundosRestantes(grupo),
                Objects.equals(grupo.getOrganizadorId(), usuarioId),
                mia.getNumero(),
                mia.getMonto(),
                mia.getEstado(),
                destino);
    }

    /** Tiempo para pagar: solo mientras el grupo está ABIERTO. */
    private long segundosRestantes(GrupoPago grupo) {
        if (grupo.getEstado() != EstadoGrupo.ABIERTO || grupo.getVenceEn() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(LocalDateTime.now(clock), grupo.getVenceEn()).toSeconds());
    }

    private static GrupoResponse.ViajeDTO viaje(Reservation r) {
        GrupoResponse.VueloDTO vuelo = CarritoCalculo.tieneVuelo(r)
                ? new GrupoResponse.VueloDTO(new ArrayList<>(r.getFlightIds()), r.getCantidadPasajeros(),
                        r.getSalidaVuelo(), r.getTarifasVuelo())
                : null;
        List<GrupoResponse.EstadiaDTO> estadias = CarritoCalculo.estadiasActivas(r).stream()
                .map(e -> new GrupoResponse.EstadiaDTO(e.getHotelNombre(), e.getCiudad(), e.getTipoHabitacionNombre(),
                        e.getCheckIn(), e.getCheckOut(), ChronoUnit.DAYS.between(e.getCheckIn(), e.getCheckOut()),
                        e.getCantidadHabitaciones(), e.getHuespedes(),
                        e.getPoliticaCancelacion() == null ? List.of() : e.getPoliticaCancelacion().stream()
                                .map(t -> new ReservationResponse.TramoDTO(t.getHorasAntes(), t.getPorcentajeReembolso()))
                                .toList()))
                .toList();
        return new GrupoResponse.ViajeDTO(vuelo, estadias);
    }
}
