package com.despescar.identityservice.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JWT Service Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_SECRET = "test-secret-key-must-be-long-enough-for-hmac-sha256-algorithm";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("generateToken should create valid JWT")
    void testGenerateToken() {
        String email = "test@example.com";
        String role = "USER";

        String token = jwtService.generateToken(email, role);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts
    }

    @Test
    @DisplayName("extractUsername should return correct email")
    void testExtractUsername() {
        String email = "test@example.com";
        String role = "USER";

        String token = jwtService.generateToken(email, role);
        String extractedEmail = jwtService.extractUsername(token);

        assertEquals(email, extractedEmail);
    }

    @Test
    @DisplayName("extractRole should return correct role")
    void testExtractRole() {
        String email = "test@example.com";
        String role = "SUPER_ADMIN";

        String token = jwtService.generateToken(email, role);
        String extractedRole = jwtService.extractRole(token);

        assertEquals(role, extractedRole);
    }

    @Test
    @DisplayName("isTokenValid should return true for valid token")
    void testIsTokenValid_ValidToken() {
        String email = "test@example.com";
        String role = "USER";

        String token = jwtService.generateToken(email, role);
        boolean isValid = jwtService.isTokenValid(token, email);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("isTokenValid should return false for wrong email")
    void testIsTokenValid_WrongEmail() {
        String email = "test@example.com";
        String role = "USER";

        String token = jwtService.generateToken(email, role);
        boolean isValid = jwtService.isTokenValid(token, "wrong@example.com");

        assertFalse(isValid);
    }

    @Test
    @DisplayName("isTokenValid should return false for invalid token")
    void testIsTokenValid_InvalidToken() {
        boolean isValid = jwtService.isTokenValid("invalid.token.here", "test@example.com");

        assertFalse(isValid);
    }

    @Test
    @DisplayName("isTokenValid should return false for expired token")
    void testIsTokenValid_ExpiredToken() throws InterruptedException {
        JwtService shortLivedService = new JwtService(TEST_SECRET, 1); // 1ms expiration
        String email = "test@example.com";

        String token = shortLivedService.generateToken(email, "USER");
        Thread.sleep(10); // Wait for token to expire

        boolean isValid = shortLivedService.isTokenValid(token, email);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("constructor should throw exception for null secret")
    void testConstructor_NullSecret() {
        assertThrows(IllegalArgumentException.class, () -> {
            new JwtService(null, EXPIRATION_MS);
        });
    }

    @Test
    @DisplayName("constructor should throw exception for empty secret")
    void testConstructor_EmptySecret() {
        assertThrows(IllegalArgumentException.class, () -> {
            new JwtService("", EXPIRATION_MS);
        });
    }

    @Test
    @DisplayName("constructor should throw exception for blank secret")
    void testConstructor_BlankSecret() {
        assertThrows(IllegalArgumentException.class, () -> {
            new JwtService("   ", EXPIRATION_MS);
        });
    }

    @Test
    @DisplayName("generateToken should handle special characters in email")
    void testGenerateToken_SpecialCharacters() {
        String email = "test+special@example.com";
        String role = "USER";

        String token = jwtService.generateToken(email, role);
        String extractedEmail = jwtService.extractUsername(token);

        assertEquals(email, extractedEmail);
    }

    @Test
    @DisplayName("generateToken should handle different roles")
    void testGenerateToken_DifferentRoles() {
        String email = "test@example.com";
        String[] roles = {"USER", "SUPER_ADMIN", "AIRLINE_ADMIN", "HOTEL_ADMIN"};

        for (String role : roles) {
            String token = jwtService.generateToken(email, role);
            String extractedRole = jwtService.extractRole(token);
            assertEquals(role, extractedRole);
        }
    }
}
