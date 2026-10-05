package com.despescar.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro estricto para los servicios con datos de usuario (reservas, pagos): un token presente pero
 * invalido, vencido o sin {@code userId} responde 401. El principal es el id del usuario y el rol
 * {@code USER} se expone como {@code ROLE_CLIENTE}.
 */
public class UserIdJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserIdJwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public UserIdJwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7).trim();
        if (jwt.isEmpty()) {
            sendUnauthorized(response);
            return;
        }

        try {
            String email = jwtService.extractUsername(jwt);
            Long userId = jwtService.extractUserId(jwt);
            String role = jwtService.extractRole(jwt);

            if (email == null || email.isBlank() || userId == null || userId <= 0
                    || role == null || role.isBlank() || !jwtService.isTokenValid(jwt, email)) {
                sendUnauthorized(response);
                return;
            }

            String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
            if ("USER".equals(normalizedRole)) {
                normalizedRole = "CLIENTE";
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole)));
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception e) {
            log.warn("JWT invalido o expirado para la request {}", request.getRequestURI(), e);
            sendUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"error\":\"Unauthorized\",\"message\":\"El token es invalido, expirado o no contiene userId.\"}");
    }
}
