package com.despescar.gatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitFilterTest {

    private final GatewayFilterChain chain = exchange -> Mono.empty();

    private HttpStatus enviar(GatewayRateLimitFilter filter, MockServerHttpRequest.BaseBuilder<?> builder, String ip) {
        ServerWebExchange exchange = MockServerWebExchange.from(builder.header("X-Forwarded-For", ip).build());
        filter.filter(exchange, chain).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }

    @Test
    void losMensajesAKoiTienenUnLimitePropioPorIp() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 2);

        assertThat(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "1.1.1.1")).isNull();
        assertThat(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions/a/messages"), "1.1.1.1")).isNull();
        ServerWebExchange tercero = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/koi/sessions/a/messages").header("X-Forwarded-For", "1.1.1.1").build());
        filter.filter(tercero, chain).block();
        assertThat(tercero.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(tercero.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");

        // Otra IP tiene su propio balde; leer el historial (GET) y el resto de la API no consumen el de KOI
        assertThat(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions/b/messages"), "2.2.2.2")).isNull();
        assertThat(enviar(filter, MockServerHttpRequest.get("/api/koi/sessions/a/messages"), "1.1.1.1")).isNull();
        assertThat(enviar(filter, MockServerHttpRequest.post("/api/bookings/carrito/estadias"), "1.1.1.1")).isNull();
    }

    @Test
    void elLimiteGeneralSigueValiendoParaTodo() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(2, 10);

        assertThat(enviar(filter, MockServerHttpRequest.get("/api/hotels"), "3.3.3.3")).isNull();
        assertThat(enviar(filter, MockServerHttpRequest.post("/api/koi/sessions"), "3.3.3.3")).isNull();
        assertThat(enviar(filter, MockServerHttpRequest.get("/api/hotels"), "3.3.3.3"))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
