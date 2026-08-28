package com.despescar.identityservice.controller;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.request.RegisterUserRequest;
import com.despescar.identityservice.dto.response.CurrentUserResponse;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.dto.response.UserResponse;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
    private UserResponse userResponse;
    private LoginResponse loginResponse;

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

        userResponse = new UserResponse(1L, "Test", "User", "test@example.com", "USER");
        loginResponse = new LoginResponse("test.jwt.token");
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
    @DisplayName("POST /auth/login should return JWT token")
    void testLogin() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test.jwt.token"));
    }

    @Test
    @DisplayName("POST /auth/login should return 400 for invalid request")
    void testLogin_InvalidRequest() throws Exception {
        LoginRequest invalidRequest = new LoginRequest();
        // Empty request

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    /* @Test
    @DisplayName("GET /auth/me should return current user")
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    void testMe() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists());
    } */

    /* @Test
    @DisplayName("GET /auth/me should return 401 when not authenticated")
    void testMe_Unauthorized() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    } */

    @Test
    @DisplayName("POST /auth/register should handle duplicate email")
    void testRegister_DuplicateEmail() throws Exception {
        when(userService.registerUser(any(RegisterUserRequest.class)))
                .thenThrow(new com.despescar.identityservice.exception.EmailAlreadyExistsException());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().is4xxClientError());
    }
}
