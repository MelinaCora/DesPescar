package com.despescar.koiiaservice.client;

import com.despescar.koiiaservice.client.dto.AirportResponse;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Endpoints públicos de flightservice: sin token. */
@Component
public class CatalogoVuelosClient {

    private final RestClient restClient;

    @Autowired
    public CatalogoVuelosClient(@Value("${koi.catalog.flight-service-url}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl));
    }

    CatalogoVuelosClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public List<AirportResponse> aeropuertos() {
        try {
            List<AirportResponse> aeropuertos = restClient.get()
                    .uri("/api/airports")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AirportResponse>>() {
                    });
            return aeropuertos == null ? List.of() : aeropuertos;
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar flightservice", ex);
        }
    }

    public BusquedaVuelosResponse buscar(String origen, String destino, LocalDate ida, LocalDate vuelta,
                                         int pasajeros) {
        try {
            BusquedaVuelosResponse respuesta = restClient.get()
                    .uri(b -> {
                        b.path("/api/flights/search")
                                .queryParam("origin", origen)
                                .queryParam("destination", destino)
                                .queryParam("departureDate", ida);
                        if (vuelta != null) {
                            b.queryParam("returnDate", vuelta);
                        }
                        return b.queryParam("passengers", pasajeros).build();
                    })
                    .retrieve()
                    .body(BusquedaVuelosResponse.class);
            return respuesta == null ? new BusquedaVuelosResponse(List.of(), List.of()) : respuesta;
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar flightservice", ex);
        }
    }
}
