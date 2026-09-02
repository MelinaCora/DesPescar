package com.despescar.koiiaservice.client;

import com.despescar.koiiaservice.client.dto.TravelPackageResponse;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PackageCatalogClient {

    private final RestClient restClient;

    public PackageCatalogClient(@Value("${koi.catalog.package-service-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<TravelPackageResponse> searchPackages(String destination, BigDecimal maxPrice) {
        try {
            List<TravelPackageResponse> packages = restClient.get()
                    .uri(uriBuilder -> {
                        var uri = uriBuilder.path("/api/packages").queryParam("active", true);
                        if (destination != null && !destination.isBlank()) {
                            uri = uri.queryParam("destination", destination);
                        }
                        if (maxPrice != null) {
                            uri = uri.queryParam("maxPrice", maxPrice);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TravelPackageResponse>>() {
                    });
            return packages == null ? List.of() : packages;
        } catch (RestClientException ex) {
            throw new KoiCatalogUnavailableException("No se pudo consultar package-service", ex);
        }
    }
}
