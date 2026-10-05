package com.despescar.reservationservice.config;

import com.despescar.common.security.InternalServiceAuthenticationFilter;
import com.despescar.common.security.JwtService;
import com.despescar.common.security.UserIdJwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.despescar.reservationservice.exception.ErrorResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            @Value("${reservation-service.sync-token:}") String paymentSyncToken,
            ObjectProvider<ObjectMapper> mapper,
            ObjectProvider<Clock> reloj) throws Exception {

        // El cuerpo lo arma el ObjectMapper (nada de JSON a mano) y la hora sale del Clock del servicio
        ObjectMapper json = mapper.getIfAvailable(() -> JsonMapper.builder().build());
        Clock clock = reloj.getIfAvailable(() -> Clock.system(ClockConfig.ZONA));

        // Rutas internas: solo payment-service, con X-Internal-Service-Token (401 sin el token correcto)
        InternalServiceAuthenticationFilter paymentSyncFilter = new InternalServiceAuthenticationFilter(
                paymentSyncToken, "payment-service", "ROLE_SERVICE_PAYMENT",
                request -> (HttpMethod.POST.matches(request.getMethod())
                        && request.getRequestURI().matches("^/api/bookings/internal/[^/]+/payment-confirmed$"))
                        || (HttpMethod.GET.matches(request.getMethod())
                        && request.getRequestURI().matches("^/api/bookings/internal/[^/]+$")));

        http
            .cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sin token: 401. Con token pero sin el rol: 403. Los dos con {codigo, mensaje, timestamp}.
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) -> escribirError(response, json, clock,
                        HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO", "Necesitás iniciar sesión."))
                .accessDeniedHandler((request, response, ex) -> escribirError(response, json, clock,
                        HttpServletResponse.SC_FORBIDDEN, "ACCESO_DENEGADO", "No tenés permisos para esta acción.")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                .requestMatchers("/ws-despescar/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bookings/flights/*/seats").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bookings/flights/*/seat-map").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bookings/internal/*").hasRole("SERVICE_PAYMENT")
                .requestMatchers(HttpMethod.POST, "/api/bookings/internal/*/payment-confirmed").hasRole("SERVICE_PAYMENT")
                .anyRequest().authenticated()
            )
            .addFilterBefore(paymentSyncFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new UserIdJwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void escribirError(HttpServletResponse response, ObjectMapper json, Clock clock, int status,
                                      String codigo, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(
                new ErrorResponse(codigo, mensaje, LocalDateTime.now(clock))));
    }
}
