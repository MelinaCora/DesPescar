package com.despescar.reservationservice.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El RestTemplate de flight-service tiene que poder mandar PATCH: con HttpURLConnection el ajuste
 * de cupos fallaba con "Invalid HTTP method: PATCH" y la disponibilidad nunca se descontaba.
 */
class FlightClientConfigTest {

    private HttpServer servidor;
    private final AtomicReference<String> metodoRecibido = new AtomicReference<>();
    private final AtomicReference<String> rutaRecibida = new AtomicReference<>();

    @BeforeEach
    void levantarServidor() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            metodoRecibido.set(intercambio.getRequestMethod());
            rutaRecibida.set(intercambio.getRequestURI().toString());
            intercambio.sendResponseHeaders(204, -1);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void bajarServidor() {
        servidor.stop(0);
    }

    @Test
    void elRestTemplateMandaPatchConSusParametros() {
        RestTemplate restTemplate = new FlightClientConfig().flightServiceRestTemplate(3000, 5000);
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort();

        ResponseEntity<Void> respuesta = restTemplate.exchange(
                base + "/api/flights/number/{flightNumber}/seats?delta={delta}",
                HttpMethod.PATCH,
                new HttpEntity<>(new HttpHeaders()),
                Void.class,
                "FO1049", -2
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(metodoRecibido.get()).isEqualTo("PATCH");
        assertThat(rutaRecibida.get()).isEqualTo("/api/flights/number/FO1049/seats?delta=-2");
    }
}
