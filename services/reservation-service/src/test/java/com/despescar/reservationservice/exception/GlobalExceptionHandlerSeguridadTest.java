package com.despescar.reservationservice.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Los rechazos de @PreAuthorize se responden directo, con el cuerpo del servicio y sin pasar por el 500. */
class GlobalExceptionHandlerSeguridadTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unUsuarioSinElRolRecibe403() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "7", null, List.of(new SimpleGrantedAuthority("ROLE_HOTEL_ADMIN"))));

        ResponseEntity<ErrorResponse> r = handler.handleSeguridad(new AccessDeniedException("Access Denied"));

        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals("ACCESO_DENEGADO", r.getBody().getCodigo());
    }

    @Test
    void unAnonimoRecibe401() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "clave", "anonimo", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        ResponseEntity<ErrorResponse> r = handler.handleSeguridad(new AccessDeniedException("Access Denied"));

        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        assertEquals("NO_AUTENTICADO", r.getBody().getCodigo());
    }

    @Test
    void unaFallaDeAutenticacionRecibe401() {
        ResponseEntity<ErrorResponse> r = handler.handleSeguridad(new BadCredentialsException("x"));

        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        assertEquals("NO_AUTENTICADO", r.getBody().getCodigo());
    }
}
