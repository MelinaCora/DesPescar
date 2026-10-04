package com.despescar.flightservice.config;

import com.despescar.flightservice.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/flights/*/seats").permitAll()
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}