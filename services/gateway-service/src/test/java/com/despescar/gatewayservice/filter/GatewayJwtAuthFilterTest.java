package com.despescar.gatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

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
    void inventoryAdjustmentPathsShouldBeBlockedEvenWithoutToken() {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);
        GatewayFilterChain chain = exchange -> Mono.empty();

        for (String path : new String[] {
                "/api/flights/number/AR1234/seats",
                "/api/hotels/3f2b8c1e-0000-0000-0000-000000000001/rooms",
                "/hoteles/3f2b8c1e-0000-0000-0000-000000000001/rooms"}) {
            ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.patch(path).build());

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).as(path).isEqualTo(HttpStatus.FORBIDDEN);
        }
        verify(jwtService, never()).parseToken(org.mockito.Mockito.anyString());
    }

    @Test
    void faresShouldBePublicToReadButRequireAnAdminRoleToWrite() {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);
        GatewayFilterChain chain = exchange -> Mono.empty();

        ServerWebExchange read = MockServerWebExchange.from(MockServerHttpRequest.get("/api/fares").build());
        filter.filter(read, chain).block();
        assertThat(read.getResponse().getStatusCode()).isNull();

        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        org.mockito.Mockito.when(claims.getSubject()).thenReturn("cliente@mail.com");
        org.mockito.Mockito.when(claims.get("role", String.class)).thenReturn("USER");
        org.mockito.Mockito.when(jwtService.parseToken("token-usuario")).thenReturn(claims);

        ServerWebExchange write = MockServerWebExchange.from(MockServerHttpRequest.post("/api/fares")
                .header("Authorization", "Bearer token-usuario").build());
        filter.filter(write, chain).block();
        assertThat(write.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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

    @Test
    void hotelReadsShouldBePublicButWritesNeedAToken() {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);
        GatewayFilterChain chain = exchange -> Mono.empty();

        for (String path : new String[] {
                "/api/hotels", "/api/hotels/destinos", "/api/hotels/3f2b8c1e-0000-0000-0000-000000000001"}) {
            ServerWebExchange read = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
            filter.filter(read, chain).block();
            assertThat(read.getResponse().getStatusCode()).as(path).isNull();
        }
        verify(jwtService, never()).parseToken(org.mockito.Mockito.anyString());

        ServerWebExchange write = MockServerWebExchange.from(MockServerHttpRequest.post("/api/hotels").build());
        filter.filter(write, chain).block();
        assertThat(write.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void internalPathsShouldBeBlockedFromOutside() {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);
        GatewayFilterChain chain = exchange -> Mono.empty();

        for (MockServerHttpRequest request : List.of(
                MockServerHttpRequest.post("/internal/retenciones").build(),
                MockServerHttpRequest.post("/internal/retenciones/abc/liberar").build(),
                MockServerHttpRequest.get("/api/bookings/internal/5").build(),
                MockServerHttpRequest.post("/hoteles/internal/retenciones")
                        .header("Authorization", "Bearer cualquiera").build(),
                MockServerHttpRequest.get("/api/payments/internal").build())) {
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode())
                    .as(request.getPath().value())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        verify(jwtService, never()).parseToken(org.mockito.Mockito.anyString());

        ServerWebExchange publica = MockServerWebExchange.from(MockServerHttpRequest.get("/api/hotels/destinos").build());
        filter.filter(publica, chain).block();
        assertThat(publica.getResponse().getStatusCode()).isNull();
    }
}
