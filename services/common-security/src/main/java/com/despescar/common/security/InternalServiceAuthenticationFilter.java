package com.despescar.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica las llamadas entre servicios (sin usuario de por medio) con un token compartido en el
 * encabezado {@code X-Internal-Service-Token}. Solo actua sobre las peticiones que cumplen
 * {@code protectedRequest}: con el token correcto concede la autoridad indicada; si falta, es distinto
 * o la variable esta vacia, responde 401 (la ruta queda cerrada).
 */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final String expectedToken;
    private final String principal;
    private final String authority;
    private final Predicate<HttpServletRequest> protectedRequest;

    public InternalServiceAuthenticationFilter(
            String expectedToken,
            String principal,
            String authority,
            Predicate<HttpServletRequest> protectedRequest) {
        this.expectedToken = expectedToken;
        this.principal = principal;
        this.authority = authority;
        this.protectedRequest = protectedRequest;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (protectedRequest.test(request)) {
            if (isValid(request.getHeader(INTERNAL_SERVICE_TOKEN_HEADER))) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(authority))));
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
}
