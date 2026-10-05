package com.despescar.koiiaservice.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Fabrica de pedidos con timeouts de conexion y lectura para los clientes del catalogo. */
final class CatalogoHttp {

    private CatalogoHttp() {
    }

    static JdkClientHttpRequestFactory factory(long connectTimeoutMs, long readTimeoutMs) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }
}
