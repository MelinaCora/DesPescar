package com.despescar.reservationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class FlightClientConfig {

    // Usa el HttpClient de java.net.http porque HttpURLConnection no admite PATCH, y el ajuste de
    // cupos de flight-service (PATCH /api/flights/number/{n}/seats) fallaba siempre con
    // "Invalid HTTP method: PATCH".
    @Bean(name = "flightServiceRestTemplate")
    public RestTemplate flightServiceRestTemplate(
            @Value("${flight-service.connection-timeout-ms:3000}") int connectionTimeoutMs,
            @Value("${flight-service.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectionTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(requestFactory);
    }
}
