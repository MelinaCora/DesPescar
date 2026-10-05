package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenEnlaceTest {

    private final TokenEnlace tokens = new TokenEnlace();

    @Test
    void generaTokensDe43CaracteresUrlSeguros() {
        String token = tokens.nuevo();

        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(TokenEnlace.formatoValido(token));
    }

    @Test
    void dosTokensNoSeRepiten() {
        assertNotEquals(tokens.nuevo(), tokens.nuevo());
    }

    @Test
    void rechazaFormatosQueNoSonDeUnEnlace() {
        assertFalse(TokenEnlace.formatoValido(null));
        assertFalse(TokenEnlace.formatoValido(""));
        assertFalse(TokenEnlace.formatoValido("12"));
        assertFalse(TokenEnlace.formatoValido("a".repeat(42)));
        assertFalse(TokenEnlace.formatoValido("a".repeat(44)));
        assertFalse(TokenEnlace.formatoValido("a".repeat(42) + "/"));
        assertFalse(TokenEnlace.formatoValido("a".repeat(42) + "="));
    }
}
