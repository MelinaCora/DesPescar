package com.despescar.gatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.server.reactive.ServerHttpRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
                MockServerHttpRequest.get("/api/payments/internal").build(),
                MockServerHttpRequest.get("/api/bookings/internal;x/1").build(),
                MockServerHttpRequest.get("/api/bookings/INTERNAL;a=b/1").build(),
                MockServerHttpRequest.get("/api/payments/internal;x").build(),
                MockServerHttpRequest.get("/api;v=1/bookings/internal/5").build())) {
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

    @Test
    void publicPathsShouldDropIdentityHeadersSentByTheClient() {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);

        for (MockServerHttpRequest request : java.util.List.of(
                MockServerHttpRequest.post("/api/koi/sessions/abc/messages").build(),
                MockServerHttpRequest.get("/api/koi/sessions/abc/messages").build(),
                MockServerHttpRequest.get("/api/hotels/destinos").build(),
                MockServerHttpRequest.get("/api/flights/search").build(),
                MockServerHttpRequest.get("/api/flights/fechas?origin=AEP&destination=COR").build())) {
            AtomicReference<ServerHttpRequest> recibido = new AtomicReference<>();
            GatewayFilterChain chain = exchange -> {
                recibido.set(exchange.getRequest());
                return Mono.empty();
            };
            ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                    .method(request.getMethod(), request.getURI())
                    .header("X-Authenticated-User", "admin@despescar.com")
                    .header("X-Authenticated-Role", "SUPER_ADMIN")
                    .build());

            filter.filter(exchange, chain).block();

            assertThat(recibido.get()).as(request.getPath().value()).isNotNull();
            assertThat(recibido.get().getHeaders().containsKey("X-Authenticated-User"))
                    .as(request.getPath().value()).isFalse();
            assertThat(recibido.get().getHeaders().containsKey("X-Authenticated-Role"))
                    .as(request.getPath().value()).isFalse();
        }
        verify(jwtService, never()).parseToken(org.mockito.Mockito.anyString());
    }

    @Test
    void protectedPathsShouldReplaceSpoofedIdentityWithTheTokenOne() {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        org.mockito.Mockito.when(claims.getSubject()).thenReturn("cliente@mail.com");
        org.mockito.Mockito.when(claims.get("role", String.class)).thenReturn("ROLE_USER");
        org.mockito.Mockito.when(jwtService.parseToken("token-cliente")).thenReturn(claims);
        AtomicReference<ServerHttpRequest> recibido = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            recibido.set(exchange.getRequest());
            return Mono.empty();
        };

        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/bookings/carrito")
                .header("Authorization", "Bearer token-cliente")
                .header("X-Authenticated-User", "admin@despescar.com")
                .header("X-Authenticated-Role", "SUPER_ADMIN")
                .build());
        filter.filter(exchange, chain).block();

        assertThat(recibido.get().getHeaders().get("X-Authenticated-User")).containsExactly("cliente@mail.com");
        assertThat(recibido.get().getHeaders().get("X-Authenticated-Role")).containsExactly("USER");
    }

    @Test
    void shouldAlsoDropTheInternalServiceTokenSentByTheClient() {
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(mock(GatewayJwtService.class));
        AtomicReference<ServerHttpRequest> recibido = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            recibido.set(exchange.getRequest());
            return Mono.empty();
        };
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/hotels/destinos")
                .header("X-Internal-Service-Token", "secreto").build());

        filter.filter(exchange, chain).block();

        assertThat(recibido.get()).isNotNull();
        assertThat(recibido.get().getHeaders().containsKey("X-Internal-Service-Token")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "OPTIONS, /api/bookings/carrito, true",
            "GET, /api/hotels/destinos, true",
            "POST, /api/koi/sessions, true",
            "GET, /api/bookings/internal/5, false",
            "POST, /internal/retenciones, false"})
    void identityHeadersAreDroppedRegardlessOfCase(String method, String path, boolean forwarded) {
        GatewayJwtService jwtService = mock(GatewayJwtService.class);
        GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter(jwtService);
        AtomicReference<ServerHttpRequest> recibido = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            recibido.set(exchange.getRequest());
            return Mono.empty();
        };
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .method(org.springframework.http.HttpMethod.valueOf(method), path)
                .header("x-authenticated-user", "admin@despescar.com")
                .header("X-AUTHENTICATED-ROLE", "SUPER_ADMIN")
                .header("x-internal-service-token", "secreto")
                .build());

        filter.filter(exchange, chain).block();

        if (!forwarded) {
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(recibido.get()).isNull();
            return;
        }
        assertThat(recibido.get()).isNotNull();
        assertThat(recibido.get().getHeaders().containsKey("X-Authenticated-User")).isFalse();
        assertThat(recibido.get().getHeaders().containsKey("X-Authenticated-Role")).isFalse();
        assertThat(recibido.get().getHeaders().containsKey("X-Internal-Service-Token")).isFalse();
    }
}
