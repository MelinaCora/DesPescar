package com.despescar.reservationservice.config;

import com.despescar.common.security.InternalServiceAuthenticationFilter;
import com.despescar.common.security.JwtService;
import com.despescar.common.security.UserIdJwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
            @Value("${reservation-service.sync-token:}") String paymentSyncToken) throws Exception {

        // Rutas internas: solo payment-service, con X-Internal-Service-Token
        InternalServiceAuthenticationFilter paymentSyncFilter = new InternalServiceAuthenticationFilter(
                paymentSyncToken, "payment-service", "ROLE_SERVICE_PAYMENT",
                request -> (HttpMethod.POST.matches(request.getMethod())
                        && request.getRequestURI().matches("^/api/bookings/internal/[^/]+/payment-confirmed$"))
                        || (HttpMethod.GET.matches(request.getMethod())
                        && request.getRequestURI().matches("^/api/bookings/internal/[^/]+$")));

        http
                .cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                    .requestMatchers("/ws-despescar/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/bookings/flights/*/seats").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/bookings/flights/*/seat-map").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/bookings/internal/*").hasRole("SERVICE_PAYMENT")
                    .requestMatchers(HttpMethod.POST, "/api/bookings/internal/*/payment-confirmed").hasRole("SERVICE_PAYMENT")
                    .anyRequest().authenticated()
            )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                        .addFilterBefore(paymentSyncFilter, UsernamePasswordAuthenticationFilter.class)
                        .addFilterBefore(new UserIdJwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}