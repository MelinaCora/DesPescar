package com.despescar.gatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.despescar.gatewayservice.security.GatewayJwtService;

import reactor.core.publisher.Mono;

class GatewayJwtAuthFilterTest {

    @Test
    void webhookPathShouldBypassJwtAuthentication() {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);
        GatewayFilterChain chain = exchange -> Mono.empty();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/payments/mercadopago/webhook")
                        .build()
        );

        filter.filter(exchange, chain).block();

        verify(jwtService, never()).parseToken(org.mockito.Mockito.anyString());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void protectedPathShouldRejectMissingBearerToken() {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);
        GatewayFilterChain chain = exchange -> Mono.empty();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/payments")
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
