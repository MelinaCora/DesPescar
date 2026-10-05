package com.despescar.hotelservice.service;

import com.despescar.hotelservice.domain.Cotizacion;
import com.despescar.hotelservice.domain.Disponibilidad;
import com.despescar.hotelservice.domain.RangoEstadia;
import com.despescar.hotelservice.domain.TextoBusqueda;
import com.despescar.hotelservice.dto.response.DestinoResponse;
import com.despescar.hotelservice.dto.response.HabitacionDetalleResponse;
import com.despescar.hotelservice.dto.response.HotelDetalleResponse;
import com.despescar.hotelservice.dto.response.HotelResumenResponse;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.Retencion;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.exception.HotelNotFoundException;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import com.despescar.hotelservice.mapper.HotelMapper;
import com.despescar.hotelservice.repository.HotelRepository;
import com.despescar.hotelservice.repository.RetencionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecturas públicas del catálogo: búsqueda, destinos y detalle con cotización por fechas. */
@Service
@Transactional(readOnly = true)
public class HotelCatalogoService {

    private static final int HUESPEDES_POR_DEFECTO = 1;
    private static final int MAX_HUESPEDES = 10;

    private final HotelRepository hotelRepository;
    private final RetencionRepository retencionRepository;
    private final HotelMapper hotelMapper;
    private final Clock clock;

    public HotelCatalogoService(HotelRepository hotelRepository, RetencionRepository retencionRepository,
                                HotelMapper hotelMapper, Clock clock) {
        this.hotelRepository = hotelRepository;
        this.retencionRepository = retencionRepository;
        this.hotelMapper = hotelMapper;
        this.clock = clock;
    }

    public List<HotelResumenResponse> buscar(String destino, LocalDate checkIn, LocalDate checkOut, Integer huespedes) {
        int cantidad = validarHuespedes(huespedes);
        Optional<RangoEstadia> rango = RangoEstadia.de(checkIn, checkOut, LocalDate.now(clock));
        String filtro = TextoBusqueda.normalizar(destino);

        List<Hotel> hoteles = hotelRepository.findByActivoTrue().stream()
                .filter(h -> filtro.isEmpty()
                        || TextoBusqueda.normalizar(h.getCiudad()).contains(filtro)
                        || TextoBusqueda.normalizar(h.getPais()).contains(filtro))
                .toList();

        List<TipoHabitacion> tipos = hoteles.stream().flatMap(h -> activas(h).stream()).toList();
        Map<UUID, List<Disponibilidad.Ocupacion>> ocupaciones =
                rango.map(r -> ocupacionesPorTipo(tipos, r)).orElse(Map.of());

        return hoteles.stream().map(h -> resumen(h, rango, cantidad, ocupaciones)).toList();
    }

    public List<DestinoResponse> destinos() {
        return hotelRepository.findByActivoTrue().stream()
                .map(h -> new DestinoResponse(h.getCiudad(), h.getPais()))
                .distinct()
                .sorted(Comparator.comparing(DestinoResponse::ciudad).thenComparing(DestinoResponse::pais))
                .toList();
    }

    public HotelDetalleResponse detalle(UUID id, LocalDate checkIn, LocalDate checkOut, Integer huespedes) {
        int cantidad = validarHuespedes(huespedes);
        Optional<RangoEstadia> rango = RangoEstadia.de(checkIn, checkOut, LocalDate.now(clock));
        Hotel hotel = hotelRepository.findByIdAndActivoTrue(id).orElseThrow(() -> new HotelNotFoundException(id));

        List<TipoHabitacion> tipos = activas(hotel);
        Map<UUID, List<Disponibilidad.Ocupacion>> ocupaciones =
                rango.map(r -> ocupacionesPorTipo(tipos, r)).orElse(Map.of());

        List<HabitacionDetalleResponse> habitaciones = tipos.stream()
                .map(t -> hotelMapper.toHabitacion(t, rango.map(r -> cotizar(t, r, cantidad, ocupaciones)).orElse(null)))
                .toList();
        return hotelMapper.toDetalle(hotel, rango.map(RangoEstadia::noches).orElse(null), habitaciones);
    }

    private HotelResumenResponse resumen(Hotel hotel, Optional<RangoEstadia> rango, int huespedes,
                                         Map<UUID, List<Disponibilidad.Ocupacion>> ocupaciones) {
        List<TipoHabitacion> tipos = activas(hotel);
        BigDecimal precioDesde = tipos.stream().map(TipoHabitacion::getPrecioPorNoche)
                .min(Comparator.naturalOrder()).orElse(null);
        if (rango.isEmpty()) {
            return hotelMapper.toResumen(hotel, precioDesde, null, null);
        }
        BigDecimal precioTotalDesde = tipos.stream()
                .map(t -> cotizar(t, rango.get(), huespedes, ocupaciones))
                .filter(Cotizacion::disponible)
                .map(Cotizacion::precioTotal)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return hotelMapper.toResumen(hotel, precioDesde, precioTotalDesde != null, precioTotalDesde);
    }

    private Cotizacion cotizar(TipoHabitacion tipo, RangoEstadia rango, int huespedes,
                               Map<UUID, List<Disponibilidad.Ocupacion>> ocupaciones) {
        return Cotizacion.calcular(tipo.getCantidadUnidades(), tipo.getCapacidad(), tipo.getPrecioPorNoche(),
                ocupaciones.getOrDefault(tipo.getId(), List.of()), rango, huespedes);
    }

    private Map<UUID, List<Disponibilidad.Ocupacion>> ocupacionesPorTipo(List<TipoHabitacion> tipos, RangoEstadia rango) {
        List<UUID> ids = tipos.stream().map(TipoHabitacion::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return retencionRepository.findActivasQueSolapan(ids, rango.checkIn(), rango.checkOut(), Instant.now(clock))
                .stream()
                .collect(Collectors.groupingBy(Retencion::getTipoHabitacionId,
                        Collectors.mapping(r -> new Disponibilidad.Ocupacion(r.getCheckIn(), r.getCheckOut(), r.getCantidad()),
                                Collectors.toList())));
    }

    private static List<TipoHabitacion> activas(Hotel hotel) {
        return hotel.getHabitaciones().stream().filter(TipoHabitacion::isActivo).toList();
    }

    private static int validarHuespedes(Integer huespedes) {
        if (huespedes == null) {
            return HUESPEDES_POR_DEFECTO;
        }
        if (huespedes < 1 || huespedes > MAX_HUESPEDES) {
            throw new SolicitudInvalidaException("Los huéspedes van de 1 a " + MAX_HUESPEDES + ".");
        }
        return huespedes;
    }
}
