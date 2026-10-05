package com.despescar.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(JwtSupport.SECRET);

    @Test
    void leeSubjectRolYUserIdDelToken() {
        String token = JwtSupport.token("ana@mail.com", 42L, "USER", 60_000);

        assertEquals("ana@mail.com", jwtService.extractUsername(token));
        assertEquals("USER", jwtService.extractRole(token));
        assertEquals(42L, jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, "ana@mail.com"));
        assertFalse(jwtService.isTokenValid(token, "otra@mail.com"));
    }

    @Test
    void tokenSinUserIdDevuelveNull() {
        assertNull(jwtService.extractUserId(JwtSupport.token("ana@mail.com", null, "USER", 60_000)));
    }

    @Test
    void tokenVencidoOFirmadoConOtraClaveNoEsValido() {
        String vencido = JwtSupport.token("ana@mail.com", 1L, "USER", -60_000);
        JwtService otraClave = new JwtService("otra-clave-distinta-de-prueba-1234567890-abc");

        assertThrows(JwtException.class, () -> jwtService.extractClaims(vencido));
        assertFalse(jwtService.isTokenValid(vencido, "ana@mail.com"));
        assertFalse(jwtService.isTokenValid("basura", "ana@mail.com"));
        assertFalse(otraClave.isTokenValid(JwtSupport.token("ana@mail.com", 1L, "USER", 60_000), "ana@mail.com"));
    }

    @Test
    void sinSecretoNoSeCreaElServicio() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(" "));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(null));
    }
}
