package com.despescar.payment_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ReservationClientConfig {

    @Bean
    RestTemplate reservationServiceRestTemplate(
            @Value("${reservation-service.connection-timeout-ms:3000}") int connectionTimeoutMs,
            @Value("${reservation-service.read-timeout-ms:5000}") int readTimeoutMs) {

        return restTemplate(connectionTimeoutMs, readTimeoutMs);
    }

    /** payment-confirmed puede tardar mas: reservation-service vuelve a tomar asientos y habitaciones. */
    @Bean
    RestTemplate reservationServiceConfirmacionRestTemplate(
            @Value("${reservation-service.connection-timeout-ms:3000}") int connectionTimeoutMs,
            @Value("${reservation-service.confirmation-read-timeout-ms:15000}") int readTimeoutMs) {

        return restTemplate(connectionTimeoutMs, readTimeoutMs);
    }

    private static RestTemplate restTemplate(int connectionTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectionTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }
}
