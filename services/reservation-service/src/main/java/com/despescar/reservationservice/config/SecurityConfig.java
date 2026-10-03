package com.despescar.reservationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final PaymentSyncAuthenticationFilter paymentSyncAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {

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
                        .addFilterBefore(paymentSyncAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}