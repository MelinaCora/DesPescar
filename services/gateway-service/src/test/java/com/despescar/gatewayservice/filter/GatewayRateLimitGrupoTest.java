package com.despescar.gatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitGrupoTest {

    private final GatewayFilterChain chain = exchange -> Mono.empty();

    private ServerWebExchange enviar(GatewayRateLimitFilter filter, MockServerHttpRequest.BaseBuilder<?> builder, String ip) {
        try {
            builder.remoteAddress(new InetSocketAddress(InetAddress.getByName(ip), 40000));
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        ServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private static boolean limitado(ServerWebExchange exchange) {
        return exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }

    @Test
    void consultarYUnirseAGruposTienenUnLimitePropioPorIp() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 10, 2, "");

        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/bookings/grupos/consultar"), "1.1.1.1"))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/bookings/grupos/unirse"), "1.1.1.1"))).isFalse();
        ServerWebExchange tercero = enviar(filter, MockServerHttpRequest.get("/api/bookings/grupos/mios"), "1.1.1.1");
        assertThat(limitado(tercero)).isTrue();
        assertThat(tercero.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");

        // Otra IP tiene su balde; el resto de la API de reservas no consume el de grupos
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/bookings/grupos/consultar"), "2.2.2.2"))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.get("/api/bookings/carrito"), "1.1.1.1"))).isFalse();
        assertThat(limitado(enviar(filter, MockServerHttpRequest.get("/api/bookings/12/grupo"), "1.1.1.1"))).isFalse();
    }

    @Test
    void elConstructorDeTresArgumentosUsaVeintePorMinuto() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(120, 10, "");

        for (int i = 0; i < 20; i++) {
            assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/bookings/grupos/consultar"), "3.3.3.3"))).isFalse();
        }
        assertThat(limitado(enviar(filter, MockServerHttpRequest.post("/api/bookings/grupos/consultar"), "3.3.3.3"))).isTrue();
    }
}
