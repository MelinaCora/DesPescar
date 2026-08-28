package com.despescar.identityservice.service;

import com.despescar.identityservice.entity.RefreshToken;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.exception.ExpiredRefreshTokenException;
import com.despescar.identityservice.exception.InvalidRefreshTokenException;
import com.despescar.identityservice.exception.RefreshTokenNotFoundException;
import com.despescar.identityservice.exception.RefreshTokenRevokedException;
import com.despescar.identityservice.exception.RefreshTokenUserNotFoundException;
import com.despescar.identityservice.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Refresh Token Service Tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 604800000L);
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
    }

    @Test
    @DisplayName("createRefreshToken should persist hashed token")
    void testCreateRefreshToken() {
        String rawToken = refreshTokenService.createRefreshToken(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken savedToken = captor.getValue();
        assertNotNull(rawToken);
        assertNotEquals(rawToken, savedToken.getTokenHash());
        assertEquals(user, savedToken.getUser());
        assertFalse(savedToken.isRevoked());
        assertNotNull(savedToken.getExpiresAt());
    }

    @Test
    @DisplayName("validateAndGetUser should return associated user for valid token")
    void testValidateAndGetUser() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

        User result = refreshTokenService.validateAndGetUser("raw-refresh-token");

        assertEquals(user, result);
    }

    @Test
    @DisplayName("validateAndGetUser should reject blank token")
    void testValidateAndGetUserBlankToken() {
        assertThrows(InvalidRefreshTokenException.class, () -> refreshTokenService.validateAndGetUser(" "));

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("validateAndGetUser should reject unknown token")
    void testValidateAndGetUserUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(RefreshTokenNotFoundException.class, () -> refreshTokenService.validateAndGetUser("raw-refresh-token"));
    }

    @Test
    @DisplayName("validateAndGetUser should reject revoked token")
    void testValidateAndGetUserRevokedToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshToken.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenRevokedException.class, () -> refreshTokenService.validateAndGetUser("raw-refresh-token"));
    }

    @Test
    @DisplayName("validateAndGetUser should reject expired token")
    void testValidateAndGetUserExpiredToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

        assertThrows(ExpiredRefreshTokenException.class, () -> refreshTokenService.validateAndGetUser("raw-refresh-token"));
    }

    @Test
    @DisplayName("validateAndGetUser should reject token without associated user")
    void testValidateAndGetUserWithoutUser() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(null);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenUserNotFoundException.class, () -> refreshTokenService.validateAndGetUser("raw-refresh-token"));
    }

    @Test
    @DisplayName("revoke should mark token as revoked")
    void testRevoke() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

        refreshTokenService.revoke("raw-refresh-token");

        assertEquals(true, refreshToken.isRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    @DisplayName("revoke should reject revoked token")
    void testRevokeAlreadyRevokedToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshToken.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenRevokedException.class, () -> refreshTokenService.revoke("raw-refresh-token"));
    }
}
