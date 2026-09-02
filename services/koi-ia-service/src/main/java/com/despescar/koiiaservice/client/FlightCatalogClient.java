package com.despescar.koiiaservice.client;

import com.despescar.koiiaservice.client.dto.FlightResponse;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class FlightCatalogClient {

    private final RestClient restClient;

    public FlightCatalogClient(@Value("${koi.catalog.flight-service-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<FlightResponse> findAllFlights() {
        try {
            List<FlightResponse> flights = restClient.get()
                    .uri("/api/flights")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<FlightResponse>>() {
                    });
            return flights == null ? List.of() : flights;
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar flightservice", ex);
        }
    }

    public FlightResponse findByNumber(String flightNumber) {
        try {
            return restClient.get()
                    .uri("/api/flights/number/{flightNumber}", flightNumber)
                    .retrieve()
                    .body(FlightResponse.class);
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar flightservice", ex);
        }
    }
}
