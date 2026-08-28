package com.despescar.identityservice.controller;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.request.RefreshTokenRequest;
import com.despescar.identityservice.dto.request.RegisterUserRequest;
import com.despescar.identityservice.dto.response.AccessTokenResponse;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.exception.AccountTemporarilyLockedException;
import com.despescar.identityservice.exception.ExpiredRefreshTokenException;
import com.despescar.identityservice.exception.RefreshTokenRevokedException;
import com.despescar.identityservice.service.AuthService;
import com.despescar.identityservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Auth Controller Integration Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthService authService;

    private RegisterUserRequest registerRequest;
    private LoginRequest loginRequest;
    private RefreshTokenRequest refreshTokenRequest;
    private UserResponse userResponse;
    private LoginResponse loginResponse;
    private AccessTokenResponse accessTokenResponse;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterUserRequest();
        registerRequest.setFirstName("Test");
        registerRequest.setLastName("User");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        refreshTokenRequest = new RefreshTokenRequest("refresh-token-value");

        userResponse = new UserResponse(1L, "Test", "User", "test@example.com", "USER");
        loginResponse = new LoginResponse(
                "access-token-value",
                "refresh-token-value",
                "Bearer",
                900
        );
        accessTokenResponse = new AccessTokenResponse("new-access-token", "Bearer", 900);
    }

    @Test
    @DisplayName("POST /auth/register should create new user")
    void testRegister() throws Exception {
        when(userService.registerUser(any(RegisterUserRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"));
    }

    @Test
    @DisplayName("POST /auth/login should return access and refresh tokens")
    void testLogin() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /auth/login should reject temporarily locked accounts")
    void testLoginLockedAccount() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new AccountTemporarilyLockedException(600));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errors.remainingSeconds").value("600"));
    }

    @Test
    @DisplayName("POST /auth/refresh should return a new access token")
    void testRefresh() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(accessTokenResponse);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshTokenRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /auth/logout should revoke refresh token")
    void testLogout() throws Exception {
        doNothing().when(authService).logout(any(RefreshTokenRequest.class));

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshTokenRequest)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /auth/login should return 400 for invalid request")
    void testLoginInvalidRequest() throws Exception {
        LoginRequest invalidRequest = new LoginRequest();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/refresh should return 400 for blank refresh token")
    void testRefreshInvalidRequest() throws Exception {
        RefreshTokenRequest invalidRequest = new RefreshTokenRequest();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/refresh should reject expired refresh tokens")
    void testRefreshExpiredToken() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class))).thenThrow(new ExpiredRefreshTokenException());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshTokenRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/logout should reject revoked refresh tokens")
    void testLogoutRevokedToken() throws Exception {
        doThrow(new RefreshTokenRevokedException()).when(authService).logout(any(RefreshTokenRequest.class));

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshTokenRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/register should handle duplicate email")
    void testRegisterDuplicateEmail() throws Exception {
        when(userService.registerUser(any(RegisterUserRequest.class)))
                .thenThrow(new com.despescar.identityservice.exception.EmailAlreadyExistsException());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict());
    }
}
