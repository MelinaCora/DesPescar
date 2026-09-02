package com.despescar.koiiaservice.client;

import com.despescar.koiiaservice.client.dto.HotelResponse;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Component
public class HotelCatalogClient {

    private final RestClient restClient;

    public HotelCatalogClient(@Value("${koi.catalog.hotel-service-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<HotelResponse> findByCity(String city) {
        try {
            List<HotelResponse> hotels = restClient.get()
                    .uri("/hoteles/ciudad/{city}", city)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<HotelResponse>>() {
                    });
            return hotels == null ? List.of() : hotels;
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar hotel-service", ex);
        }
    }

    public HotelResponse findById(UUID hotelId) {
        try {
            return restClient.get()
                    .uri("/hoteles/{id}", hotelId)
                    .retrieve()
                    .body(HotelResponse.class);
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar hotel-service", ex);
        }
    }
}
