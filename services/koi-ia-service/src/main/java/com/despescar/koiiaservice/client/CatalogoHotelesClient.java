package com.despescar.koiiaservice.client;

import com.despescar.koiiaservice.client.dto.HotelDetalleResponse;
import com.despescar.koiiaservice.client.dto.HotelResumenResponse;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Endpoints públicos de hotel-service (búsqueda y detalle con cotización): sin token. */
@Component
public class CatalogoHotelesClient {

    private final RestClient restClient;

    @Autowired
    public CatalogoHotelesClient(@Value("${koi.catalog.hotel-service-url}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl));
    }

    CatalogoHotelesClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public List<HotelResumenResponse> buscar(String destino, LocalDate checkIn, LocalDate checkOut, int huespedes) {
        try {
            List<HotelResumenResponse> hoteles = restClient.get()
                    .uri(b -> b.path("/hoteles")
                            .queryParam("destino", destino)
                            .queryParam("checkIn", checkIn)
                            .queryParam("checkOut", checkOut)
                            .queryParam("huespedes", huespedes)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<HotelResumenResponse>>() {
                    });
            return hoteles == null ? List.of() : hoteles;
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar hotel-service", ex);
        }
    }

    public HotelDetalleResponse detalle(UUID id, LocalDate checkIn, LocalDate checkOut, int huespedes) {
        try {
            return restClient.get()
                    .uri(b -> b.path("/hoteles/{id}")
                            .queryParam("checkIn", checkIn)
                            .queryParam("checkOut", checkOut)
                            .queryParam("huespedes", huespedes)
                            .build(id))
                    .retrieve()
                    .body(HotelDetalleResponse.class);
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar hotel-service", ex);
        }
    }
}
