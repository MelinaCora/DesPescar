package com.despescar.flightservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica las llamadas internas de reservation-service que ajustan el inventario.
 * Exige el encabezado X-Internal-Service-Token con el valor de INVENTORY_SERVICE_TOKEN;
 * si la variable esta vacia, la ruta queda cerrada.
 */
@Component
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final String expectedToken;

    public InternalServiceAuthenticationFilter(@Value("${inventory.sync-token:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (matchesInventoryEndpoint(request)) {
            String providedToken = request.getHeader(INTERNAL_SERVICE_TOKEN_HEADER);
            if (isValid(providedToken)) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "reservation-service",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SERVICE_RESERVATION"))
                ));
            } else {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service token.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValid(String providedToken) {
        if (expectedToken == null || expectedToken.isBlank() || providedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }

    private boolean matchesInventoryEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null
                && HttpMethod.PATCH.matches(request.getMethod())
                && uri.matches("^/api/flights/number/[^/]+/seats$");
    }
}
