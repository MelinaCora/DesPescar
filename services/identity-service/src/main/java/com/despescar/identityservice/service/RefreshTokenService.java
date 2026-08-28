package com.despescar.identityservice.service;

import com.despescar.identityservice.entity.RefreshToken;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.exception.ExpiredRefreshTokenException;
import com.despescar.identityservice.exception.InvalidRefreshTokenException;
import com.despescar.identityservice.exception.RefreshTokenNotFoundException;
import com.despescar.identityservice.exception.RefreshTokenRevokedException;
import com.despescar.identityservice.exception.RefreshTokenUserNotFoundException;
import com.despescar.identityservice.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusNanos(refreshTokenExpirationMs * 1_000_000));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional(readOnly = true)
    public User validateAndGetUser(String rawToken) {
        RefreshToken refreshToken = findRefreshToken(rawToken);

        if (refreshToken.isRevoked()) {
            throw new RefreshTokenRevokedException();
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredRefreshTokenException();
        }

        User user = refreshToken.getUser();
        if (user == null) {
            throw new RefreshTokenUserNotFoundException();
        }

        return user;
    }

    @Transactional
    public void revoke(String rawToken) {
        RefreshToken refreshToken = findRefreshToken(rawToken);

        if (refreshToken.isRevoked()) {
            throw new RefreshTokenRevokedException();
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationMs / 1000;
    }

    private RefreshToken findRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        Optional<RefreshToken> refreshToken = refreshTokenRepository.findByTokenHash(hashToken(rawToken));
        return refreshToken.orElseThrow(RefreshTokenNotFoundException::new);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular el hash del refresh token", e);
        }
    }
}
