package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.client.CatalogoHotelesClient;
import com.despescar.koiiaservice.client.CatalogoVuelosClient;
import com.despescar.koiiaservice.client.dto.AirportResponse;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.Tarifa;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.VueloBuscado;
import com.despescar.koiiaservice.client.dto.HotelDetalleResponse;
import com.despescar.koiiaservice.client.dto.HotelResumenResponse;
import com.despescar.koiiaservice.recomendador.AeropuertosPorLugar;
import com.despescar.koiiaservice.recomendador.HabitacionCandidata;
import com.despescar.koiiaservice.recomendador.HotelCandidato;
import com.despescar.koiiaservice.recomendador.VueloCandidato;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Trae del catálogo público los candidatos para el recomendador. Los precios de vuelo se toman
 * de la tarifa (transparentFinalPrice, por pasajero) y se suman como ARS (spec: moneda única).
 */
@Service
public class KoiCatalogo {

    static final int MAX_HOTELES = 8;

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

    public List<HotelCandidato> hoteles(String destino, LocalDate checkIn, LocalDate checkOut, int viajeros) {
        return hotelesClient.buscar(destino, checkIn, checkOut, viajeros).stream()
                .filter(h -> Boolean.TRUE.equals(h.disponible()))
                .sorted(Comparator.comparing(HotelResumenResponse::precioTotalDesde,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_HOTELES)
                .map(h -> candidato(h, hotelesClient.detalle(h.id(), checkIn, checkOut, viajeros)))
                .toList();
    }

    /** Un vuelo con su tarifa más barata; vacío si no tiene tarifas con precio o sus fechas no se leen. */
    public static Optional<VueloCandidato> candidato(VueloBuscado vuelo) {
        if (vuelo == null || vuelo.id() == null || vuelo.itinerary() == null
                || vuelo.itinerary().departure() == null || vuelo.itinerary().arrival() == null) {
            return Optional.empty();
        }
        Optional<Tarifa> masBarata = vuelo.fares() == null ? Optional.empty() : vuelo.fares().stream()
                .filter(t -> t.id() != null && t.price() != null && t.price().transparentFinalPrice() != null)
                .min(Comparator.comparing(t -> t.price().transparentFinalPrice()));
        if (masBarata.isEmpty()) {
            return Optional.empty();
        }
        try {
            LocalDateTime salida = LocalDateTime.parse(vuelo.itinerary().departure().dateTime());
            LocalDateTime llegada = LocalDateTime.parse(vuelo.itinerary().arrival().dateTime());
            String aerolinea = vuelo.airline() == null || vuelo.airline().name() == null
                    ? "Aerolínea" : vuelo.airline().name();
            BigDecimal precio = masBarata.get().price().transparentFinalPrice();
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
