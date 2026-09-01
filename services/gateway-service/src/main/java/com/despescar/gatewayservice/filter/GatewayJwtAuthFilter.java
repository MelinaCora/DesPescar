package com.despescar.gatewayservice.filter;

import com.despescar.gatewayservice.security.GatewayJwtService;
import com.despescar.gatewayservice.util.GatewayResponseWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Set;

@Component
public class GatewayJwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> HOTEL_ROLES = Set.of("SUPER_ADMIN", "HOTEL_ADMIN");
    private static final Set<String> AIRLINE_ROLES = Set.of("SUPER_ADMIN", "AIRLINE_ADMIN");
    private static final Set<String> SUPER_ADMIN_ROLE = Set.of("SUPER_ADMIN");

    private final GatewayJwtService jwtService;

    public GatewayJwtAuthFilter(GatewayJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().value();

        if (method == HttpMethod.OPTIONS || isPublicPath(path)) {
            return chain.filter(exchange);
        }

        if (!requiresAuthentication(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return GatewayResponseWriter.writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Se requiere token Bearer."
            );
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        Claims claims;
        try {
            claims = jwtService.parseToken(token);
        } catch (JwtException ex) {
            return GatewayResponseWriter.writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Token invalido o expirado."
            );
        }

        String username = claims.getSubject();
        String role = normalizeRole(claims.get("role", String.class));
        if (isBlank(username) || isBlank(role)) {
            return GatewayResponseWriter.writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Token sin claims requeridos."
            );
        }

        Set<String> requiredRoles = requiredRolesFor(path, method);
        if (!requiredRoles.isEmpty() && !requiredRoles.contains(role)) {
            return GatewayResponseWriter.writeError(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "Forbidden",
                    "No tienes permisos para este recurso."
            );
        }

        ServerHttpRequest requestWithClaims = exchange.getRequest().mutate()
                .header("X-Authenticated-User", username)
                .header("X-Authenticated-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(requestWithClaims).build());
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/")
                || path.equals("/api/auth")
                || path.startsWith("/actuator/")
                || path.equals("/actuator")
                || path.startsWith("/fallback/");
    }

    private boolean requiresAuthentication(String path) {
        return path.startsWith("/api/") || path.startsWith("/hoteles/");
    }

    private Set<String> requiredRolesFor(String path, HttpMethod method) {
        if (method == null) {
            return Set.of();
        }

        if (path.startsWith("/api/users")) {
            if (path.equals("/api/users/me") || path.equals("/api/users/me/roles")) {
                return Set.of();
            }
            return SUPER_ADMIN_ROLE;
        }

        if (path.startsWith("/api/packages")) {
            return method == HttpMethod.GET ? Set.of() : SUPER_ADMIN_ROLE;
        }

        if (path.startsWith("/api/flights")
                || path.startsWith("/api/airlines")
                || path.startsWith("/api/airports")
                || path.startsWith("/api/baggage-policies")) {
            if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE) {
                return AIRLINE_ROLES;
            }
            return Set.of();
        }

        if (path.startsWith("/api/hotels") || path.startsWith("/hoteles")) {
            if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE) {
                return HOTEL_ROLES;
            }
            return Set.of();
        }

        return Set.of();
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring("ROLE_".length());
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
