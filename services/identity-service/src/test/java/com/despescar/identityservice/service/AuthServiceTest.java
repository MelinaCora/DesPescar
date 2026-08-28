package com.despescar.identityservice.service;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.entity.UserRole;
import com.despescar.identityservice.exception.InvalidCredentialsException;
import com.despescar.identityservice.repository.UserRepository;
import com.despescar.identityservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Service Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

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

        UserRole ur = new UserRole();
        ur.setRole(userRole);
        testUser.addRole(ur);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("plainPassword");
    }

    @Test
    @DisplayName("login should return token for valid credentials")
    void testLogin_ValidCredentials() {
        when(userRepository.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
                .thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString()))
                .thenReturn("test.jwt.token");

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("test.jwt.token", response.getToken());
        verify(userRepository).findByEmail(loginRequest.getEmail());
        verify(passwordEncoder).matches(loginRequest.getPassword(), testUser.getPassword());
        verify(jwtService).generateToken(testUser.getEmail(), testUser.getPrimaryRoleName());
    }

    @Test
    @DisplayName("login should throw exception for non-existent user")
    void testLogin_UserNotFound() {
        when(userRepository.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> {
            authService.login(loginRequest);
        });

        verify(userRepository).findByEmail(loginRequest.getEmail());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("login should throw exception for invalid password")
    void testLogin_InvalidPassword() {
        when(userRepository.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
                .thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> {
            authService.login(loginRequest);
        });

        verify(userRepository).findByEmail(loginRequest.getEmail());
        verify(passwordEncoder).matches(loginRequest.getPassword(), testUser.getPassword());
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("login should generate token with correct role")
    void testLogin_WithSuperAdminRole() {
        Role superAdminRole = new Role();
        superAdminRole.setId(2L);
        superAdminRole.setName("SUPER_ADMIN");

        UserRole ur = new UserRole();
        ur.setRole(superAdminRole);
        testUser.addRole(ur);

        when(userRepository.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
                .thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString()))
                .thenReturn("test.jwt.token");

        authService.login(loginRequest);

        verify(jwtService).generateToken(testUser.getEmail(), "SUPER_ADMIN");
    }
}
