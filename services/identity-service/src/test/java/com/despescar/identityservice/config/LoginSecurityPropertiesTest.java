package com.despescar.identityservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DisplayName("Login Security Properties Tests")
class LoginSecurityPropertiesTest {

    @Value("${security.login.max-attempts}")
    private int maxAttempts;

    @Value("${security.login.lock-duration-ms}")
    private long lockDurationMs;

    @Test
    @DisplayName("should load login protection values from properties")
    void shouldLoadLoginProtectionValuesFromProperties() {
        assertEquals(5, maxAttempts);
        assertEquals(900000L, lockDurationMs);
    }
}
