package com.despescar.flightservice.config;

import com.despescar.common.security.InternalServiceAuthenticationFilter;
import com.despescar.common.security.JwtAuthenticationFilter;
import com.despescar.common.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
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

        // Ajuste de asientos: solo reservation-service, con X-Internal-Service-Token
        InternalServiceAuthenticationFilter inventoryFilter = new InternalServiceAuthenticationFilter(
                inventoryToken, "reservation-service", "ROLE_SERVICE_RESERVATION",
                request -> HttpMethod.PATCH.matches(request.getMethod())
                        && request.getRequestURI().matches("^/api/flights/number/[^/]+/seats$"));

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/flights/number/*/seats").hasRole("SERVICE_RESERVATION")
                        .requestMatchers(HttpMethod.GET, "/api/flights/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/flights").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/flights/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/airports").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/fares").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/airports/code/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("SUPER_ADMIN", "AIRLINE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("SUPER_ADMIN", "AIRLINE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("SUPER_ADMIN", "AIRLINE_ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(inventoryFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
