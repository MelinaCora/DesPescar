package com.despescar.identityservice.service;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.request.RefreshTokenRequest;
import com.despescar.identityservice.dto.response.AccessTokenResponse;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.exception.InvalidCredentialsException;
import com.despescar.identityservice.repository.UserRepository;
import com.despescar.identityservice.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        boolean validPassword = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!validPassword) {
            throw new InvalidCredentialsException();
        }

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
}