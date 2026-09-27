package com.despescar.identityservice.config;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.despescar.identityservice.security.JwtAuthenticationFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
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
				// 1. Agrega esto para activar el soporte de CORS en Spring Security
				.cors(Customizer.withDefaults())

				.csrf(csrf -> csrf.disable())

				.sessionManagement(session ->
						session.sessionCreationPolicy(
								SessionCreationPolicy.STATELESS
						)
				)

				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/auth/register",
								"/auth/login",
								"/auth/refresh",
								"/auth/logout",
								"/swagger-ui/**",
								"/v3/api-docs/**"
						).permitAll()
						.anyRequest()
						.authenticated()
				)

				.addFilterBefore(
						jwtAuthenticationFilter,
						UsernamePasswordAuthenticationFilter.class
				);

		return http.build();
	}

	// 2. Define esta configuración global de CORS que leerá Spring Security
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();

		// Autoriza el origen exacto de tu app de React
		configuration.setAllowedOrigins(List.of("http://localhost:5173"));

		// Habilita los métodos HTTP necesarios
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

		// Permite cabeceras comunes (Content-Type, Authorization, etc.)
		configuration.setAllowedHeaders(List.of("*"));

		// Habilita el intercambio de cookies/credenciales si lo necesitas
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		// Aplica esta regla a todos los endpoints del backend
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
