package com.despescar.identityservice.config;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.despescar.identityservice.security.JwtAuthenticationFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
	
	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	public SecurityConfig(
	        JwtAuthenticationFilter jwtAuthenticationFilter
	) {
	    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(
	        HttpSecurity http
	) throws Exception {

	    http

	        .csrf(csrf -> csrf.disable())

	        .sessionManagement(session ->

	            session.sessionCreationPolicy(
	                    SessionCreationPolicy.STATELESS //solo puede usar JWT (no cookies, no sesiones)
	            )
	        )

	        .authorizeHttpRequests(auth -> auth

	            .requestMatchers(

	                    "/auth/**",
	                    "/swagger-ui/**",
	                    "/v3/api-docs/**"

	            ).permitAll()

	            .anyRequest()
	            .authenticated()
	        )

	        .addFilterBefore( //le dice a spring que antes de autenticar usuarios ejecute filter

	                jwtAuthenticationFilter,

	                UsernamePasswordAuthenticationFilter.class
	        );

	    return http.build();
	}
    
}