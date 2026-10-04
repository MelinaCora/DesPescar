package com.despescar.hotelservice.config;

import com.despescar.hotelservice.security.InternalServiceAuthenticationFilter;
import com.despescar.hotelservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalServiceAuthenticationFilter internalServiceAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Ajuste de habitaciones: solo reservation-service, con X-Internal-Service-Token
                .requestMatchers(HttpMethod.PATCH, "/hoteles/*/rooms").hasRole("SERVICE_RESERVATION")
                .requestMatchers(HttpMethod.GET, "/hoteles/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/hoteles/**").hasAnyRole("SUPER_ADMIN", "HOTEL_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/hoteles/**").hasAnyRole("SUPER_ADMIN", "HOTEL_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/hoteles/**").hasAnyRole("SUPER_ADMIN", "HOTEL_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalServiceAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}