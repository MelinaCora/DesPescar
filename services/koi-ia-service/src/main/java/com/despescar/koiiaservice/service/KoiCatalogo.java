package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.client.CatalogoHotelesClient;
import com.despescar.koiiaservice.client.CatalogoVuelosClient;
import com.despescar.koiiaservice.client.dto.AirportResponse;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.Tarifa;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.VueloBuscado;
import com.despescar.koiiaservice.client.dto.HotelDetalleResponse;
import com.despescar.koiiaservice.client.dto.HotelResumenResponse;
import com.despescar.koiiaservice.client.dto.VueloListadoResponse;
import com.despescar.koiiaservice.recomendador.AeropuertosPorLugar;
import com.despescar.koiiaservice.recomendador.HabitacionCandidata;
import com.despescar.koiiaservice.recomendador.HotelCandidato;
import com.despescar.koiiaservice.recomendador.VueloCandidato;
import com.despescar.koiiaservice.recomendador.VueloProgramado;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Trae del catálogo público los candidatos para el recomendador. El precio por pasajero de un
 * vuelo es su precio base (price.baseFare) más la tarifa elegida (transparentFinalPrice), igual
 * que lo que cobra el carrito; solo se usan precios en ARS (spec: moneda única).
 */
@Slf4j
@Service
public class KoiCatalogo {

    static final int MAX_HOTELES = 8;
    private static final String ARS = "ARS";

    private final CatalogoVuelosClient vuelosClient;
    private final CatalogoHotelesClient hotelesClient;

    public KoiCatalogo(CatalogoVuelosClient vuelosClient, CatalogoHotelesClient hotelesClient) {
        this.vuelosClient = vuelosClient;
        this.hotelesClient = hotelesClient;
    }

    public record VuelosCandidatos(List<VueloCandidato> idas, List<VueloCandidato> vueltas) {
    }

    public VuelosCandidatos vuelos(String origen, String destino, LocalDate ida, LocalDate vuelta, int viajeros) {
        List<AirportResponse> aeropuertos = vuelosClient.aeropuertos();
        List<String> origenes = AeropuertosPorLugar.resolver(aeropuertos, origen);
        List<String> destinos = AeropuertosPorLugar.resolver(aeropuertos, destino);
        List<VueloCandidato> idas = new ArrayList<>();
        List<VueloCandidato> vueltas = new ArrayList<>();
        for (String o : origenes) {
            for (String d : destinos) {
                if (o.equals(d)) {
                    continue;
                }
                BusquedaVuelosResponse r = vuelosClient.buscar(o, d, ida, vuelta, viajeros);
                agregar(idas, r.departureFlights());
                agregar(vueltas, r.returnFlights());
            }
        }
        return new VuelosCandidatos(idas, vueltas);
    }

    public List<AirportResponse> aeropuertos() {
        return vuelosClient.aeropuertos();
    }

    /** Ciudades con hoteles, en el orden del catálogo. */
    public List<String> destinos() {
        return hotelesClient.destinos().stream()
                .map(d -> d == null ? null : d.ciudad())
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();
    }

    /** Ruta y día de cada vuelo publicado; se omiten los que no se pueden leer. */
    public List<VueloProgramado> vuelosProgramados() {
        return vuelosClient.todos().stream()
                .map(KoiCatalogo::programado)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<VueloProgramado> programado(VueloListadoResponse v) {
        if (v == null || v.originAirport() == null || v.destinationAirport() == null || v.departureTime() == null
                || v.originAirport().code() == null || v.destinationAirport().code() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new VueloProgramado(v.originAirport().code(), v.destinationAirport().code(),
                    LocalDateTime.parse(v.departureTime()).toLocalDate()));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    public List<HotelCandidato> hoteles(String destino, LocalDate checkIn, LocalDate checkOut, int viajeros) {
        return hotelesClient.buscar(destino, checkIn, checkOut, viajeros).stream()
                .filter(h -> Boolean.TRUE.equals(h.disponible()))
                .sorted(Comparator.comparing(HotelResumenResponse::precioTotalDesde,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_HOTELES)
                .map(h -> detalleSeguro(h, checkIn, checkOut, viajeros))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<HotelCandidato> detalleSeguro(HotelResumenResponse h, LocalDate checkIn, LocalDate checkOut,
                                                   int viajeros) {
        try {
            return Optional.of(candidato(h, hotelesClient.detalle(h.id(), checkIn, checkOut, viajeros)));
        } catch (KoiCatalogUnavailableException ex) {
            log.warn("KOI omite el hotel {}: no se pudo traer su detalle", h.id(), ex);
            return Optional.empty();
        }
    }

    /** Un vuelo con su tarifa más barata; vacío si no tiene tarifas con precio o sus fechas no se leen. */
    public static Optional<VueloCandidato> candidato(VueloBuscado vuelo) {
        if (vuelo == null || vuelo.id() == null || vuelo.itinerary() == null
                || vuelo.itinerary().departure() == null || vuelo.itinerary().arrival() == null) {
            return Optional.empty();
        }
        if (vuelo.price() == null || vuelo.price().baseFare() == null || !ARS.equals(vuelo.price().currency())) {
            return Optional.empty();
        }
        Optional<Tarifa> masBarata = vuelo.fares() == null ? Optional.empty() : vuelo.fares().stream()
                .filter(t -> t.id() != null && t.price() != null && t.price().transparentFinalPrice() != null
                        && ARS.equals(t.price().currency()))
                .min(Comparator.comparing(t -> t.price().transparentFinalPrice()));
        if (masBarata.isEmpty()) {
            return Optional.empty();
        }
        try {
            LocalDateTime salida = LocalDateTime.parse(vuelo.itinerary().departure().dateTime());
            LocalDateTime llegada = LocalDateTime.parse(vuelo.itinerary().arrival().dateTime());
            String aerolinea = vuelo.airline() == null || vuelo.airline().name() == null
                    ? "Aerolínea" : vuelo.airline().name();
            BigDecimal precio = vuelo.price().baseFare().add(masBarata.get().price().transparentFinalPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            return Optional.of(new VueloCandidato(vuelo.id(), masBarata.get().id(), aerolinea,
                    vuelo.flightNumber(), salida, llegada, precio));
        } catch (DateTimeParseException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    private static void agregar(List<VueloCandidato> destino, List<VueloBuscado> vuelos) {
        if (vuelos == null) {
            return;
        }
        vuelos.stream().map(KoiCatalogo::candidato).flatMap(Optional::stream).forEach(destino::add);
    }

    private static HotelCandidato candidato(HotelResumenResponse resumen, HotelDetalleResponse detalle) {
        List<HabitacionCandidata> habitaciones = detalle == null || detalle.habitaciones() == null ? List.of()
                : detalle.habitaciones().stream()
                .filter(Objects::nonNull)
                .filter(h -> h.id() != null && h.unidadesLibres() != null && h.precioPorNoche() != null)
                .map(h -> new HabitacionCandidata(h.id(), h.nombre(), h.capacidad(), h.precioPorNoche(),
                        h.unidadesLibres()))
                .toList();
        return new HotelCandidato(resumen.id(), resumen.nombre(), resumen.ciudad(), resumen.estrellas(),
                resumen.imagenPrincipal(), habitaciones);
    }
}
