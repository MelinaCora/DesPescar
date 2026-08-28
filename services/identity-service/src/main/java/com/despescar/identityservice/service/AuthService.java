package com.despescar.identityservice.service;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.request.RefreshTokenRequest;
import com.despescar.identityservice.dto.response.AccessTokenResponse;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.exception.AccountTemporarilyLockedException;
import com.despescar.identityservice.exception.InvalidCredentialsException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import com.despescar.identityservice.repository.UserRepository;
import com.despescar.identityservice.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de autenticacion.
 *
 * <p>Al iniciar sesion se genera un JWT con el rol principal del usuario. Los roles
 * adicionales y sus alcances se resuelven desde la entidad UserRole.</p>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final int maxLoginAttempts;
    private final long loginLockDurationMs;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            @Value("${security.login.max-attempts:5}") int maxLoginAttempts,
            @Value("${security.login.lock-duration-ms:900000}") long loginLockDurationMs
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.maxLoginAttempts = maxLoginAttempts;
        this.loginLockDurationMs = loginLockDurationMs;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        LocalDateTime now = LocalDateTime.now();
        User user = userRepository
                .findByEmailForUpdate(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        clearExpiredLock(user, now);

        if (isLocked(user, now)) {
            throw new AccountTemporarilyLockedException(remainingLockSeconds(user, now));
        }

        boolean validPassword = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!validPassword) {
            handleFailedLogin(user, now);
        }

        resetLoginState(user);

        String accessToken = jwtService.generateToken(
                user.getEmail(),
                user.getPrimaryRoleName()
        );
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds()
        );
    }

    public AccessTokenResponse refresh(RefreshTokenRequest request) {
        User user = refreshTokenService.validateAndGetUser(request.getRefreshToken());
        String accessToken = jwtService.generateToken(user.getEmail(), user.getPrimaryRoleName());

        return new AccessTokenResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds()
        );
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
    }

    private void handleFailedLogin(User user, LocalDateTime now) {
        int nextFailedAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(nextFailedAttempts);

        if (nextFailedAttempts >= maxLoginAttempts) {
            user.setLockedUntil(now.plus(Duration.ofMillis(loginLockDurationMs)));
            userRepository.save(user);
            throw new AccountTemporarilyLockedException(Duration.ofMillis(loginLockDurationMs).toSeconds());
        }

        userRepository.save(user);
        throw new InvalidCredentialsException();
    }

    private void clearExpiredLock(User user, LocalDateTime now) {
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    private boolean isLocked(User user, LocalDateTime now) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    }

    private long remainingLockSeconds(User user, LocalDateTime now) {
        return Math.max(1, Duration.between(now, user.getLockedUntil()).toSeconds());
    }

    private void resetLoginState(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}