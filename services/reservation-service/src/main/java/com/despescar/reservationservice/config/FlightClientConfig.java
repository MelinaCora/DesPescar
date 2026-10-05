package com.despescar.reservationservice.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class FlightClientConfig {

    @Bean(name = "flightServiceRestTemplate")
    public RestTemplate flightServiceRestTemplate(
            @Value("${flight-service.connection-timeout-ms:3000}") int connectionTimeoutMs,
            @Value("${flight-service.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        // Configuramos los timeouts usando las clases nativas de HttpClient 5
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        // Le pasamos el cliente configurado a la fábrica (esto habilita PATCH automáticamente)
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(requestFactory);
    }
}