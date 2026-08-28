package com.despescar.identityservice.service;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.request.RefreshTokenRequest;
import com.despescar.identityservice.dto.response.AccessTokenResponse;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.entity.UserRole;
import com.despescar.identityservice.exception.AccountTemporarilyLockedException;
import com.despescar.identityservice.exception.InvalidCredentialsException;
import com.despescar.identityservice.repository.UserRepository;
import com.despescar.identityservice.security.JwtService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private AuthService authService;
    private User testUser;
    private User secondUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                5,
                900000L
        );

        testUser = createUser(1L, "test@example.com", "USER");
        secondUser = createUser(2L, "other@example.com", "USER");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("plainPassword");
    }

    @Test
    @DisplayName("login should return access and refresh token for valid credentials")
    void testLoginValidCredentials() {
        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
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
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockedUntil());
    }

    @Test
    @DisplayName("login should throw exception for non-existent user")
    void testLoginUserNotFound() {
        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("login should increment failed attempts on invalid password")
    void testLoginInvalidPasswordIncrementsCounter() {
        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));

        assertEquals(1, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockedUntil());
        verify(userRepository).save(testUser);
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("login should keep increasing the failed attempts counter")
    void testLoginInvalidPasswordKeepsCounter() {
        testUser.setFailedLoginAttempts(3);

        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));

        assertEquals(4, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockedUntil());
    }

    @Test
    @DisplayName("login should lock the account on the fifth failed attempt")
    void testLoginFifthFailedAttemptLocksAccount() {
        testUser.setFailedLoginAttempts(4);

        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(false);

        AccountTemporarilyLockedException exception = assertThrows(
                AccountTemporarilyLockedException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(5, testUser.getFailedLoginAttempts());
        assertNotNull(testUser.getLockedUntil());
        assertTrue(exception.getRemainingSeconds() >= 900L);
        verify(refreshTokenService, never()).createRefreshToken(testUser);
    }

    @Test
    @DisplayName("locked account should reject login even with correct password")
    void testLockedAccountRejectsValidPassword() {
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));

        assertThrows(AccountTemporarilyLockedException.class, () -> authService.login(loginRequest));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(refreshTokenService, never()).createRefreshToken(testUser);
    }

    @Test
    @DisplayName("expired lock should allow login and reset lock state")
    void testExpiredLockAllowsLoginAgain() {
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("refresh-token");

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockedUntil());
    }

    @Test
    @DisplayName("successful login should reset failed attempts and clear lock state")
    void testSuccessfulLoginResetsLockState() {
        testUser.setFailedLoginAttempts(2);
        testUser.setLockedUntil(null);

        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("refresh-token");

        authService.login(loginRequest);

        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockedUntil());
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("lock should not affect other users")
    void testLockDoesNotAffectOtherUsers() {
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));

        LoginRequest secondLoginRequest = new LoginRequest("other@example.com", "plainPassword");

        when(userRepository.findByEmailForUpdate("other@example.com")).thenReturn(Optional.of(secondUser));
        when(passwordEncoder.matches(secondLoginRequest.getPassword(), secondUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(secondUser)).thenReturn("refresh-token");

        LoginResponse response = authService.login(secondLoginRequest);

        assertNotNull(response);
        assertEquals(5, testUser.getFailedLoginAttempts());
        assertEquals(0, secondUser.getFailedLoginAttempts());
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

        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("refresh-token");

        authService.login(loginRequest);

        verify(jwtService).generateToken(testUser.getEmail(), "SUPER_ADMIN");
    }

    @Test
    @DisplayName("configured values should control the lock threshold and duration")
    void testConfiguredValuesControlLockingBehavior() {
        AuthService configuredAuthService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                3,
                60000L
        );
        testUser.setFailedLoginAttempts(2);

        when(userRepository.findByEmailForUpdate(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(false);

        AccountTemporarilyLockedException exception = assertThrows(
                AccountTemporarilyLockedException.class,
                () -> configuredAuthService.login(loginRequest)
        );

        assertEquals(3, testUser.getFailedLoginAttempts());
        assertTrue(exception.getRemainingSeconds() >= 60L);
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

    private User createUser(Long id, String email, String roleName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPassword("encodedPassword");
        user.setFirstName("Test");
        user.setLastName("User");

        Role role = new Role();
        role.setId(id);
        role.setName(roleName);

        UserRole assignment = new UserRole();
        assignment.setRole(role);
        user.addRole(assignment);
        return user;
    }
}
