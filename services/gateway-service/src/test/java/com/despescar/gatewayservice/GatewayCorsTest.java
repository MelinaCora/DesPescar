package com.despescar.gatewayservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCorsTest {

    private static final String ORIGIN = "http://localhost:5173";
    private static HttpServer identityStub;

    @Autowired
    private WebTestClient webTestClient;

    // Simula identity-service sin cabeceras CORS propias, en un puerto libre.
    static {
        try {
            identityStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        identityStub.createContext("/auth", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        identityStub.start();
    }

    @TestConfiguration
    static class StubRouteConfig {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        RouteLocator stubAuthRoutes(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("stub-identity-auth", r -> r.order(-100)
                            .path("/api/auth/**")
                            .filters(f -> f.rewritePath("/api/auth/?(?<segment>.*)", "/auth/${segment}"))
                            .uri("http://localhost:" + identityStub.getAddress().getPort()))
                    .build();
        }
    }

    @AfterAll
    static void stopIdentityStub() {
        if (identityStub != null) {
            identityStub.stop(0);
        }
    }

    @Test
    void preflightShouldReturnSingleAllowOriginHeader() {
        webTestClient.method(HttpMethod.OPTIONS)
                .uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN);
    }

    @Test
    void loginAndRegisterShouldReturnSingleAllowOriginHeader() {
        assumeTrue(identityStub != null, "Puerto 8080 ocupado; no se puede simular identity-service");

        for (String path : new String[] {"/api/auth/login", "/api/auth/register"}) {
            webTestClient.post()
                    .uri(path)
                    .header(HttpHeaders.ORIGIN, ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
                    .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                            value -> assertThat(value).doesNotContain(","));
        }
    }
}
