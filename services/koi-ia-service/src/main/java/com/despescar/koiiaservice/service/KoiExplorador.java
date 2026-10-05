package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.client.dto.AirportResponse;
import com.despescar.koiiaservice.dto.response.KoiRecommendationResponse;
import com.despescar.koiiaservice.enums.TipoOpcion;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import com.despescar.koiiaservice.recomendador.AeropuertosPorLugar;
import com.despescar.koiiaservice.recomendador.FechasFlexibles;
import com.despescar.koiiaservice.recomendador.HotelCandidato;
import com.despescar.koiiaservice.recomendador.PedidoRecomendacion;
import com.despescar.koiiaservice.recomendador.Recomendador;
import com.despescar.koiiaservice.recomendador.VueloProgramado;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Propone viajes cuando el usuario no eligió destino: recorre los destinos con hoteles, arma el
 * mejor combo de cada uno con el catálogo real y devuelve hasta 3 de destinos distintos dentro
 * del presupuesto (o las 2 más cercanas si ninguno entra). Un destino que falla se saltea; si
 * fallan todos, el catálogo se considera caído. Si con las fechas que dio el usuario no sale
 * ninguna opción, se busca una vez más en la ventana flexible.
 */
@Slf4j
@Service
public class KoiExplorador {

    public static final int MAX_DESTINOS = 6;
    public static final int NOCHES_POR_DEFECTO = 3;
    public static final int VENTANA_DIAS = 60;
    /** Si el usuario fijó una fecha sin vuelos, hasta cuántos días después se busca. */
    static final int DIAS_DESPUES_DE_LA_FECHA = 14;

    private final KoiCatalogo catalogo;

    public KoiExplorador(KoiCatalogo catalogo) {
        this.catalogo = catalogo;
    }

    public List<KoiRecommendationResponse> explorar(PedidoExploracion pedido) {
        List<String> destinos = catalogo.destinos();
        List<AirportResponse> aeropuertos = catalogo.aeropuertos();
        List<VueloProgramado> programados = catalogo.vuelosProgramados();
        List<KoiRecommendationResponse> opciones = buscar(pedido, destinos, aeropuertos, programados);
        if (opciones.isEmpty() && (pedido.fechaIda() != null || pedido.mesIda() != null)) {
            PedidoExploracion sinFechas = new PedidoExploracion(pedido.presupuesto(), pedido.viajeros(),
                    pedido.origen(), null, null, null, pedido.hoy());
            return buscar(sinFechas, destinos, aeropuertos, programados);
        }
        return opciones;
    }

    private List<KoiRecommendationResponse> buscar(PedidoExploracion pedido, List<String> destinos,
                                                   List<AirportResponse> aeropuertos,
                                                   List<VueloProgramado> programados) {
        List<String> origenes = AeropuertosPorLugar.resolver(aeropuertos, pedido.origen());
        LocalDate desde = desde(pedido);
        LocalDate hasta = hasta(pedido, desde);
        int noches = pedido.noches() != null ? pedido.noches() : NOCHES_POR_DEFECTO;

        List<KoiRecommendationResponse> dentro = new ArrayList<>();
        List<KoiRecommendationResponse> cercanas = new ArrayList<>();
        int intentos = 0;
        int fallas = 0;
        for (String destino : destinos) {
            if (intentos >= MAX_DESTINOS) {
                break;
            }
            List<String> codigos = AeropuertosPorLugar.resolver(aeropuertos, destino);
            if (codigos.isEmpty() || codigos.stream().anyMatch(origenes::contains)) {
                continue;
            }
            Optional<FechasFlexibles.Estadia> estadia =
                    FechasFlexibles.elegir(programados, origenes, codigos, desde, hasta, noches);
            if (estadia.isEmpty()) {
                continue;
            }
            intentos++;
            List<KoiRecommendationResponse> opciones;
            try {
                opciones = combos(pedido, destino, estadia.get());
            } catch (RuntimeException ex) {
                fallas++;
                log.warn("KOI saltea el destino {} al explorar: {}", destino, ex.toString());
                continue;
            }
            if (opciones.isEmpty()) {
                continue;
            }
            KoiRecommendationResponse mejor = opciones.get(0);
            (mejor.excedeEn() == null ? dentro : cercanas).add(mejor);
        }
        if (intentos > 0 && fallas == intentos) {
            throw new KoiCatalogUnavailableException("Ningún destino respondió al explorar", null);
        }
        if (!dentro.isEmpty()) {
            return dentro.stream()
                    .sorted(Comparator.comparing(KoiRecommendationResponse::total))
                    .limit(Recomendador.MAX_OPCIONES)
                    .toList();
        }
        return cercanas.stream()
                .sorted(Comparator.comparing(KoiRecommendationResponse::excedeEn))
                .limit(Recomendador.MAX_CERCANAS)
                .toList();
    }

    private List<KoiRecommendationResponse> combos(PedidoExploracion pedido, String destino,
                                                   FechasFlexibles.Estadia estadia) {
        KoiCatalogo.VuelosCandidatos vuelos =
                catalogo.vuelos(pedido.origen(), destino, estadia.ida(), estadia.vuelta(), pedido.viajeros());
        List<HotelCandidato> hoteles = catalogo.hoteles(destino, estadia.ida(), estadia.vuelta(), pedido.viajeros());
        return Recomendador.recomendar(
                new PedidoRecomendacion(TipoOpcion.COMBO, pedido.presupuesto(), pedido.viajeros(), estadia.ida(),
                        estadia.vuelta()),
                vuelos.idas(), vuelos.vueltas(), hoteles);
    }

    private static LocalDate desde(PedidoExploracion p) {
        LocalDate manana = p.hoy().plusDays(1);
        if (p.fechaIda() != null) {
            return p.fechaIda().isBefore(manana) ? manana : p.fechaIda();
        }
        if (p.mesIda() != null) {
            LocalDate inicio = p.mesIda().atDay(1);
            return inicio.isBefore(manana) ? manana : inicio;
        }
        return manana;
    }

    private static LocalDate hasta(PedidoExploracion p, LocalDate desde) {
        if (p.fechaIda() != null) {
            return desde.plusDays(DIAS_DESPUES_DE_LA_FECHA);
        }
        if (p.mesIda() != null) {
            return p.mesIda().atEndOfMonth();
        }
        return p.hoy().plusDays(VENTANA_DIAS);
    }
}
