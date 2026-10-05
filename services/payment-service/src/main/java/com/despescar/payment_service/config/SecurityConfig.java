package com.despescar.payment_service.config;

import java.io.IOException;
import java.time.LocalDateTime;

import com.despescar.common.security.InternalServiceAuthenticationFilter;
import com.despescar.common.security.JwtService;
import com.despescar.common.security.UserIdJwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            @Value("${payment-service.sync-token:}") String reservationSyncToken) throws Exception {

        // Rutas internas: solo reservation-service, con X-Internal-Service-Token (401 sin el token correcto)
        InternalServiceAuthenticationFilter reservationSyncFilter = new InternalServiceAuthenticationFilter(
                reservationSyncToken, "reservation-service", "ROLE_SERVICE_RESERVATION",
                request -> HttpMethod.POST.matches(request.getMethod())
                        && request.getRequestURI().matches("^/api/payments/internal/(grupos/[^/]+/reembolsos|reservas/[^/]+/reembolso)$"));

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                // La firma del webhook la valida MercadoPagoWebhookSignatureValidator.
                .requestMatchers(HttpMethod.POST, "/api/payments/mercadopago/webhook").permitAll()
                // Hasta E4 los reembolsos los inicia el sistema (pago tardio, monto distinto); un
                // cliente no puede reembolsarse un pago y quedarse con la reserva confirmada.
                .requestMatchers(HttpMethod.POST, "/api/refunds").denyAll()
                // Reembolsos de un pago en grupo: los pide reservation-service con su token (CB5).
                .requestMatchers(HttpMethod.POST, "/api/payments/internal/grupos/*/reembolsos").hasRole("SERVICE_RESERVATION")
                // Reembolso de una reserva cancelada por su dueño: tambien lo pide reservation-service.
                .requestMatchers(HttpMethod.POST, "/api/payments/internal/reservas/*/reembolso").hasRole("SERVICE_RESERVATION")
                .anyRequest().authenticated()
            )
            .exceptionHandling(errores -> errores
                .authenticationEntryPoint((request, response, ex) ->
                    escribirError(request, response, HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesion."))
                .accessDeniedHandler((request, response, ex) ->
                    escribirError(request, response, HttpStatus.FORBIDDEN, "No tienes permisos para este recurso.")))
            .addFilterBefore(reservationSyncFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new UserIdJwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Mismo cuerpo que GlobalExceptionHandler: {timestamp, status, error, message, path} (D24). */
    static void escribirError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String mensaje) throws IOException {

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now()
                + "\",\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + mensaje
                + "\",\"path\":\"" + escaparJson(request.getRequestURI()) + "\"}");
    }

    private static String escaparJson(String texto) {
        return texto == null ? "" : texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
