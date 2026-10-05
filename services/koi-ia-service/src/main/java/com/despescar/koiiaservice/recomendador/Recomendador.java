package com.despescar.koiiaservice.recomendador;

import com.despescar.koiiaservice.dto.response.KoiHotelOpcion;
import com.despescar.koiiaservice.dto.response.KoiRecommendationResponse;
import com.despescar.koiiaservice.dto.response.KoiVueloOpcion;
import com.despescar.koiiaservice.enums.TipoOpcion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Arma las opciones de KOI (spec 3.7) a partir de candidatos del catálogo. Función pura: no
 * consulta servicios ni la hora.
 *  - Vuelo: ida (+ vuelta que salga después de llegar la ida), (tarifa ida + vuelta) x viajeros.
 *  - Hotel: por hotel, el tipo de habitación más barato con unidades libres suficientes para
 *    ceil(viajeros / capacidad) habitaciones; precio por noche x noches x habitaciones.
 *  - Combina, ordena por total, filtra por presupuesto y devuelve hasta 3 priorizando hoteles
 *    distintos (o idas distintas si no hay hotel). Si ninguna entra, las 2 más cercanas con
 *    excedeEn.
 */
public final class Recomendador {

    public static final int MAX_OPCIONES = 3;
    public static final int MAX_CERCANAS = 2;
    static final int MAX_VUELOS_POR_TRAMO = 10;
    private static final String MONEDA = "ARS";

    private Recomendador() {
    }

    public static List<KoiRecommendationResponse> recomendar(PedidoRecomendacion pedido, List<VueloCandidato> idas,
                                                             List<VueloCandidato> vueltas,
                                                             List<HotelCandidato> hoteles) {
        if (pedido.viajeros() < 1) {
            return List.of();
        }
        idas = conPrecio(idas);
        vueltas = conPrecio(vueltas);
        hoteles = conHabitacionesValidas(hoteles);
        List<KoiVueloOpcion> vuelos = pedido.tipo() == TipoOpcion.HOTEL ? List.of() : itinerarios(pedido, idas, vueltas);
        List<KoiHotelOpcion> estadias = pedido.tipo() == TipoOpcion.VUELO ? List.of() : estadias(pedido, hoteles);

        Stream<Candidata> candidatas = switch (pedido.tipo()) {
            case VUELO -> vuelos.stream().map(v -> new Candidata(v, null));
            case HOTEL -> estadias.stream().map(h -> new Candidata(null, h));
            case COMBO -> vuelos.stream().flatMap(v -> estadias.stream().map(h -> new Candidata(v, h)));
        };
        List<Candidata> ordenadas = candidatas.sorted(POR_TOTAL).toList();

        BigDecimal presupuesto = pedido.presupuesto();
        if (presupuesto == null) {
            return armar(pedido, elegir(ordenadas, MAX_OPCIONES), null);
        }
        List<Candidata> dentro = ordenadas.stream().filter(c -> c.total().compareTo(presupuesto) <= 0).toList();
        if (!dentro.isEmpty()) {
            return armar(pedido, elegir(dentro, MAX_OPCIONES), presupuesto);
        }
        return armar(pedido, elegir(ordenadas, MAX_CERCANAS), presupuesto);
    }

    /** Desempate estable: a igual total, por ids de vuelo/habitacion y fechas. */
    private static final Comparator<Candidata> POR_TOTAL = Comparator.comparing(Candidata::total)
            .thenComparing(Candidata::desempate);

    private static List<VueloCandidato> conPrecio(List<VueloCandidato> vuelos) {
        return vuelos.stream().filter(v -> v.precioTarifa() != null).toList();
    }

    private static List<HotelCandidato> conHabitacionesValidas(List<HotelCandidato> hoteles) {
        return hoteles.stream()
                .map(h -> new HotelCandidato(h.hotelId(), h.nombre(), h.ciudad(), h.estrellas(), h.imagen(),
                        h.habitaciones().stream()
                                .filter(hab -> hab.precioPorNoche() != null && hab.unidadesLibres() > 0)
                                .toList()))
                .filter(h -> !h.habitaciones().isEmpty())
                .toList();
    }

    private static List<KoiVueloOpcion> itinerarios(PedidoRecomendacion pedido, List<VueloCandidato> idas,
                                                    List<VueloCandidato> vueltas) {
        BigDecimal viajeros = BigDecimal.valueOf(pedido.viajeros());
        List<VueloCandidato> idasBaratas = masBaratos(idas);
        if (pedido.checkOut() == null) {
            return idasBaratas.stream().map(ida -> vuelo(ida, null, viajeros)).toList();
        }
        List<VueloCandidato> vueltasBaratas = masBaratos(vueltas);
        List<KoiVueloOpcion> resultado = new ArrayList<>();
        for (VueloCandidato ida : idasBaratas) {
            for (VueloCandidato vuelta : vueltasBaratas) {
                if (vuelta.salida().isAfter(ida.llegada())) {
                    resultado.add(vuelo(ida, vuelta, viajeros));
                }
            }
        }
        return resultado;
    }

    private static List<VueloCandidato> masBaratos(List<VueloCandidato> vuelos) {
        return vuelos.stream()
                .sorted(Comparator.comparing(VueloCandidato::precioTarifa)
                        .thenComparing(VueloCandidato::numero, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(VueloCandidato::flightId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_VUELOS_POR_TRAMO)
                .toList();
    }

    private static KoiVueloOpcion vuelo(VueloCandidato ida, VueloCandidato vuelta, BigDecimal viajeros) {
        BigDecimal tarifas = vuelta == null ? ida.precioTarifa() : ida.precioTarifa().add(vuelta.precioTarifa());
        String aerolinea = vuelta == null || Objects.equals(ida.aerolinea(), vuelta.aerolinea())
                ? ida.aerolinea() : ida.aerolinea() + " / " + vuelta.aerolinea();
        return new KoiVueloOpcion(ida.flightId(), vuelta == null ? null : vuelta.flightId(),
                ida.fareId(), vuelta == null ? null : vuelta.fareId(), aerolinea,
                ida.numero(), vuelta == null ? null : vuelta.numero(),
                ida.salida(), ida.llegada(),
                vuelta == null ? null : vuelta.salida(), vuelta == null ? null : vuelta.llegada(),
                tarifas.multiply(viajeros).setScale(2, RoundingMode.HALF_UP));
    }

    private static List<KoiHotelOpcion> estadias(PedidoRecomendacion pedido, List<HotelCandidato> hoteles) {
        if (pedido.checkIn() == null || pedido.checkOut() == null) {
            return List.of();
        }
        int noches = (int) ChronoUnit.DAYS.between(pedido.checkIn(), pedido.checkOut());
        if (noches < 1) {
            return List.of();
        }
        return hoteles.stream()
                .map(h -> estadia(h, pedido, noches))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<KoiHotelOpcion> estadia(HotelCandidato hotel, PedidoRecomendacion pedido, int noches) {
        int viajeros = pedido.viajeros();
        return hotel.habitaciones().stream()
                .filter(h -> h.capacidad() > 0)
                .filter(h -> habitacionesNecesarias(viajeros, h.capacidad()) <= h.unidadesLibres())
                .map(h -> {
                    int cantidad = habitacionesNecesarias(viajeros, h.capacidad());
                    BigDecimal precio = h.precioPorNoche()
                            .multiply(BigDecimal.valueOf((long) noches * cantidad))
                            .setScale(2, RoundingMode.HALF_UP);
                    return new KoiHotelOpcion(hotel.hotelId(), hotel.nombre(), hotel.ciudad(), hotel.estrellas(),
                            hotel.imagen(), h.id(), h.nombre(), pedido.checkIn(), pedido.checkOut(), noches,
                            cantidad, viajeros, precio);
                })
                .min(Comparator.comparing(KoiHotelOpcion::precio)
                        .thenComparing(KoiHotelOpcion::tipoHabitacionNombre,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(KoiHotelOpcion::tipoHabitacionId));
    }

    static int habitacionesNecesarias(int viajeros, int capacidad) {
        return (viajeros + capacidad - 1) / capacidad;
    }

    /** Primero una por clave (hotel o ida) en orden de precio; después completa; queda ordenada. */
    private static List<Candidata> elegir(List<Candidata> ordenadas, int cuantas) {
        List<Candidata> elegidas = new ArrayList<>();
        Set<String> claves = new HashSet<>();
        for (Candidata c : ordenadas) {
            if (elegidas.size() < cuantas && claves.add(c.clave())) {
                elegidas.add(c);
            }
        }
        for (Candidata c : ordenadas) {
            if (elegidas.size() < cuantas && !elegidas.contains(c)) {
                elegidas.add(c);
            }
        }
        return elegidas.stream().sorted(POR_TOTAL).toList();
    }

    private static List<KoiRecommendationResponse> armar(PedidoRecomendacion pedido, List<Candidata> elegidas,
                                                         BigDecimal presupuesto) {
        return elegidas.stream().map(c -> {
            BigDecimal total = c.total();
            BigDecimal excede = presupuesto != null && total.compareTo(presupuesto) > 0
                    ? total.subtract(presupuesto).setScale(2, RoundingMode.HALF_UP) : null;
            return new KoiRecommendationResponse(optionId(pedido, c), pedido.tipo(), c.vuelo(), c.hotel(),
                    pedido.viajeros(), total, MONEDA, excede,
                    Motivos.de(pedido.tipo(), c.vuelo(), c.hotel(), total, presupuesto));
        }).toList();
    }

    private static String optionId(PedidoRecomendacion pedido, Candidata c) {
        String clave = Stream.of(pedido.tipo(), pedido.viajeros(),
                        c.vuelo() == null ? null : c.vuelo().departureFlightId(),
                        c.vuelo() == null ? null : c.vuelo().departureFareId(),
                        c.vuelo() == null ? null : c.vuelo().returnFlightId(),
                        c.vuelo() == null ? null : c.vuelo().returnFareId(),
                        c.hotel() == null ? null : c.hotel().tipoHabitacionId(),
                        c.hotel() == null ? null : c.hotel().checkIn(),
                        c.hotel() == null ? null : c.hotel().checkOut())
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
        return UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record Candidata(KoiVueloOpcion vuelo, KoiHotelOpcion hotel) {

        BigDecimal total() {
            BigDecimal total = BigDecimal.ZERO;
            if (vuelo != null) {
                total = total.add(vuelo.precio());
            }
            if (hotel != null) {
                total = total.add(hotel.precio());
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        }

        String desempate() {
            return (hotel == null ? "" : hotel.hotelNombre() + "|" + hotel.hotelId() + "|" + hotel.tipoHabitacionId())
                    + "#" + (vuelo == null ? "" : vuelo.numeroIda() + "|" + vuelo.numeroVuelta() + "|"
                    + vuelo.departureFlightId() + "|" + vuelo.returnFlightId());
        }

        String clave() {
            return hotel != null ? "h:" + hotel.hotelId() : "v:" + vuelo.departureFlightId();
        }
    }
}
