package com.despescar.hotelservice.config;

import com.despescar.common.security.InternalServiceAuthenticationFilter;
import com.despescar.common.security.JwtAuthenticationFilter;
import com.despescar.common.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                            @Value("${inventory.sync-token:}") String inventoryToken) throws Exception {
        // API interna de retenciones: solo reservation-service, con X-Internal-Service-Token.
        // Sin el token correcto (o con la variable vacía) responde 401: la ruta queda cerrada.
        InternalServiceAuthenticationFilter internos = new InternalServiceAuthenticationFilter(
                inventoryToken, "reservation-service", "ROLE_SERVICE_RESERVATION",
                request -> request.getRequestURI().startsWith("/internal/"));

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sin token: 401. Con token pero sin el rol: 403. Los dos con {error}, como el resto.
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) ->
                        escribirError(response, HttpServletResponse.SC_UNAUTHORIZED, "Necesitás iniciar sesión."))
                .accessDeniedHandler((request, response, ex) ->
                        escribirError(response, HttpServletResponse.SC_FORBIDDEN, "No tenés permisos para esta acción.")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                // Catálogo público, como la búsqueda de vuelos
                .requestMatchers(HttpMethod.GET, "/hoteles", "/hoteles/destinos", "/hoteles/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/hoteles").hasAnyRole("SUPER_ADMIN", "HOTEL_ADMIN")
                .requestMatchers("/internal/**").hasRole("SERVICE_RESERVATION")
                .anyRequest().authenticated()
            )
            .addFilterBefore(internos, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void escribirError(HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
