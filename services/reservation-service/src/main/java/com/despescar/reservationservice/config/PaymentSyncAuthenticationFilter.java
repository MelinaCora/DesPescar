package com.despescar.reservationservice.config;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PaymentSyncAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final String expectedToken;

    public PaymentSyncAuthenticationFilter(
            @Value("${reservation-service.sync-token:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (matchesPaymentSyncEndpoint(request)) {
            String providedToken = request.getHeader(INTERNAL_SERVICE_TOKEN_HEADER);
            if (expectedToken != null && !expectedToken.isBlank() && expectedToken.equals(providedToken)) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "payment-service",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SERVICE_PAYMENT"))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service token.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean matchesPaymentSyncEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return (HttpMethod.POST.matches(request.getMethod())
                && uri.matches("^/api/bookings/internal/[^/]+/payment-confirmed$"))
                || (HttpMethod.GET.matches(request.getMethod())
                && uri.matches("^/api/bookings/internal/[^/]+$"));
    }
}
