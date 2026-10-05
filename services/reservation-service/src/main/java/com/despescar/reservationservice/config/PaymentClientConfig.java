package com.despescar.reservationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PaymentClientConfig {

    /** Lectura más larga que la de hotel: reembolsar varias partes llama al proveedor una vez por parte. */
    @Bean(name = "paymentServiceRestTemplate")
    public RestTemplate paymentServiceRestTemplate(
            @Value("${payment-service.connection-timeout-ms:3000}") int connectionTimeoutMs,
            @Value("${payment-service.read-timeout-ms:10000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectionTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }
}
