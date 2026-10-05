package com.despescar.hotelservice.config;

import com.despescar.common.security.InternalServiceAuthenticationFilter;
import com.despescar.common.security.JwtAuthenticationFilter;
import com.despescar.common.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            @Value("${inventory.sync-token:}") String inventoryToken) throws Exception {

        // Ajuste de habitaciones: solo reservation-service, con X-Internal-Service-Token
        InternalServiceAuthenticationFilter inventoryFilter = new InternalServiceAuthenticationFilter(
                inventoryToken, "reservation-service", "ROLE_SERVICE_RESERVATION",
                request -> HttpMethod.PATCH.matches(request.getMethod())
                        && request.getRequestURI().matches("^/hoteles/[^/]+/rooms$"));

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/hoteles/*/rooms").hasRole("SERVICE_RESERVATION")
                .requestMatchers(HttpMethod.GET, "/hoteles/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/hoteles/**").hasAnyRole("SUPER_ADMIN", "HOTEL_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/hoteles/**").hasAnyRole("SUPER_ADMIN", "HOTEL_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/hoteles/**").hasAnyRole("SUPER_ADMIN", "HOTEL_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(inventoryFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
