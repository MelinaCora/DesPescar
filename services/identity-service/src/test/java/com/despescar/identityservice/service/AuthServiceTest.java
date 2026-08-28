package com.despescar.identityservice.service;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.request.RefreshTokenRequest;
import com.despescar.identityservice.dto.response.AccessTokenResponse;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.entity.UserRole;
import com.despescar.identityservice.exception.InvalidCredentialsException;
import com.despescar.identityservice.repository.UserRepository;
import com.despescar.identityservice.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Service Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setFirstName("Test");
        testUser.setLastName("User");

        Role userRole = new Role();
        userRole.setId(1L);
        userRole.setName("USER");

        UserRole userAssignment = new UserRole();
        userAssignment.setRole(userRole);
        testUser.addRole(userAssignment);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("plainPassword");
    }

    @Test
    @DisplayName("login should return access and refresh token for valid credentials")
    void testLoginValidCredentials() {
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("refresh-token");

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresIn());
        verify(jwtService).generateToken(testUser.getEmail(), testUser.getPrimaryRoleName());
        verify(refreshTokenService).createRefreshToken(testUser);
    }

    @Test
    @DisplayName("login should throw exception for non-existent user")
    void testLoginUserNotFound() {
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("login should throw exception for invalid password")
    void testLoginInvalidPassword() {
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));

        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("login should generate access token with highest role")
    void testLoginWithSuperAdminRole() {
        Role superAdminRole = new Role();
        superAdminRole.setId(2L);
        superAdminRole.setName("SUPER_ADMIN");

        UserRole superAdminAssignment = new UserRole();
        superAdminAssignment.setRole(superAdminRole);
        testUser.addRole(superAdminAssignment);

        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("refresh-token");

        authService.login(loginRequest);

        verify(jwtService).generateToken(testUser.getEmail(), "SUPER_ADMIN");
    }

    @Test
    @DisplayName("refresh should return a new access token")
    void testRefresh() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

        when(refreshTokenService.validateAndGetUser("refresh-token")).thenReturn(testUser);
        when(jwtService.generateToken(testUser.getEmail(), testUser.getPrimaryRoleName())).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AccessTokenResponse response = authService.refresh(request);

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresIn());
    }

    @Test
    @DisplayName("logout should revoke the refresh token")
    void testLogout() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

        authService.logout(request);

        verify(refreshTokenService).revoke("refresh-token");
    }
}
