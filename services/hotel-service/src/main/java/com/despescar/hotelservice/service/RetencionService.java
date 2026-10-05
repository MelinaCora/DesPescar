package com.despescar.hotelservice.service;

import com.despescar.hotelservice.domain.Disponibilidad;
import com.despescar.hotelservice.domain.PrecioEstadia;
import com.despescar.hotelservice.domain.RangoEstadia;
import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.dto.internal.RetencionRequest;
import com.despescar.hotelservice.dto.internal.RetencionResponse;
import com.despescar.hotelservice.entity.EstadoRetencion;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.Retencion;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.exception.ConflictoException;
import com.despescar.hotelservice.exception.HotelNotFoundException;
import com.despescar.hotelservice.exception.RetencionNoEncontradaException;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import com.despescar.hotelservice.repository.RetencionRepository;
import com.despescar.hotelservice.repository.TipoHabitacionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Retenciones de habitaciones para el carrito de reservation-service (API interna, contrato C1). */
@Service
public class RetencionService {

    static final String MONEDA = "ARS";
    static final String SIN_DISPONIBILIDAD = "SIN_DISPONIBILIDAD";
    static final String RETENCION_LIBERADA = "RETENCION_LIBERADA";

    private final TipoHabitacionRepository tipoRepository;
    private final RetencionRepository retencionRepository;
    private final Clock clock;

    public RetencionService(TipoHabitacionRepository tipoRepository, RetencionRepository retencionRepository,
                            Clock clock) {
        this.tipoRepository = tipoRepository;
        this.retencionRepository = retencionRepository;
        this.clock = clock;
    }

    /** Toma unidades de un tipo para un rango. El lock sobre el tipo evita vender dos veces la última unidad. */
    @Transactional
    public RetencionResponse crear(RetencionRequest pedido) {
        TipoHabitacion tipo = tipoRepository.findByIdForUpdate(pedido.tipoHabitacionId())
                .filter(t -> t.isActivo() && t.getHotel().isActivo()
                        && t.getHotel().getId().equals(pedido.hotelId()))
                .orElseThrow(() -> new HotelNotFoundException("La habitación elegida no existe o no está disponible."));
        Instant ahora = Instant.now(clock);
        RangoEstadia rango = RangoEstadia.de(pedido.checkIn(), pedido.checkOut(), LocalDate.now(clock))
                .orElseThrow(() -> new SolicitudInvalidaException("Indicá check-in y check-out."));
        if ((long) tipo.getCapacidad() * pedido.cantidad() < pedido.huespedes()) {
            throw new SolicitudInvalidaException(
                    "Las habitaciones elegidas no alcanzan para " + pedido.huespedes() + " huéspedes.");
        }
        if (!pedido.expiraEn().isAfter(ahora)) {
            throw new SolicitudInvalidaException("El vencimiento de la retención ya pasó.");
        }
        exigirLugar(tipo, rango, pedido.cantidad(), ahora);

        Retencion retencion = new Retencion();
        retencion.setReservaId(pedido.reservaId());
        retencion.setUsuarioId(pedido.usuarioId());
        retencion.setTipoHabitacionId(tipo.getId());
        retencion.setCheckIn(rango.checkIn());
        retencion.setCheckOut(rango.checkOut());
        retencion.setCantidad(pedido.cantidad());
        retencion.setHuespedes(pedido.huespedes());
        retencion.setEstado(EstadoRetencion.RETENIDA);
        retencion.setExpiraEn(pedido.expiraEn());
        return respuesta(retencionRepository.save(retencion), tipo);
    }

    /**
     * Confirma con el nombre del titular. Idempotente. Una RETENIDA vencida ya no ocupa lugar:
     * se confirma solo si las unidades siguen libres (pago tardío).
     */
    @Transactional
    public RetencionResponse confirmar(UUID id, String nombreTitular) {
        Retencion retencion = buscar(id);
        if (retencion.getEstado() == EstadoRetencion.LIBERADA) {
            throw new ConflictoException(RETENCION_LIBERADA, "La retención ya fue liberada.");
        }
        TipoHabitacion tipo = tipoRepository.findByIdForUpdate(retencion.getTipoHabitacionId())
                .orElseThrow(() -> new HotelNotFoundException("La habitación de la retención ya no existe."));
        if (retencion.getEstado() == EstadoRetencion.RETENIDA) {
            Instant ahora = Instant.now(clock);
            if (!retencion.getExpiraEn().isAfter(ahora)) {
                exigirLugar(tipo, new RangoEstadia(retencion.getCheckIn(), retencion.getCheckOut()),
                        retencion.getCantidad(), ahora);
            }
            retencion.setEstado(EstadoRetencion.CONFIRMADA);
            retencion.setNombreTitular(nombreTitular.trim());
        }
        return respuesta(retencion, tipo);
    }

    /** Devuelve las unidades. Idempotente; acepta también confirmadas (cancelaciones de E4). */
    @Transactional
    public void liberar(UUID id) {
        buscar(id).setEstado(EstadoRetencion.LIBERADA);
    }

    private Retencion buscar(UUID id) {
        return retencionRepository.findById(id).orElseThrow(() -> new RetencionNoEncontradaException(id));
    }

    private void exigirLugar(TipoHabitacion tipo, RangoEstadia rango, int cantidad, Instant ahora) {
        List<Disponibilidad.Ocupacion> ocupaciones = retencionRepository
                .findActivasQueSolapan(List.of(tipo.getId()), rango.checkIn(), rango.checkOut(), ahora)
                .stream()
                .map(r -> new Disponibilidad.Ocupacion(r.getCheckIn(), r.getCheckOut(), r.getCantidad()))
                .toList();
        int libres = Disponibilidad.unidadesLibres(tipo.getCantidadUnidades(), ocupaciones,
                rango.checkIn(), rango.checkOut());
        if (libres < cantidad) {
            throw new ConflictoException(SIN_DISPONIBILIDAD, "No quedan habitaciones de ese tipo para esas fechas.");
        }
    }

    private RetencionResponse respuesta(Retencion r, TipoHabitacion tipo) {
        Hotel hotel = tipo.getHotel();
        RangoEstadia rango = new RangoEstadia(r.getCheckIn(), r.getCheckOut());
        List<TramoDto> politica = hotel.getPoliticaCancelacion().stream()
                .map(t -> new TramoDto(t.getHorasAntes(), t.getPorcentajeReembolso()))
                .toList();
        return new RetencionResponse(r.getId(), hotel.getId(), hotel.getNombre(), hotel.getCiudad(),
                tipo.getId(), tipo.getNombre(), r.getCheckIn(), r.getCheckOut(), rango.noches(),
                r.getCantidad(), r.getHuespedes(),
                PrecioEstadia.total(tipo.getPrecioPorNoche(), rango, r.getCantidad()), MONEDA,
                hotel.getHoraCheckIn(), hotel.getZonaHoraria(), politica, r.getEstado(), r.getExpiraEn());
    }
}
