package com.despescar.identityservice.controller;

import com.despescar.identityservice.dto.request.AssignRoleRequest;
import com.despescar.identityservice.dto.response.RoleResponse;
import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.entity.UserRole;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("User Controller Integration Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private UserResponse userResponse;
    private User mockUser;

    @BeforeEach
    void setUp() {
        userResponse = new UserResponse(1L, "Test", "User", "test@example.com", "USER");
        
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@example.com");
        mockUser.setFirstName("Test");
        mockUser.setLastName("User");
        
        Role userRole = new Role();
        userRole.setId(1L);
        userRole.setName("USER");
        
        UserRole ur = new UserRole();
        ur.setRole(userRole);
        mockUser.addRole(ur);
    }

    /* @Test
    @DisplayName("GET /users/me should return current user profile")
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    void testMyProfile() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists());
    } */

    /* @Test
    @DisplayName("GET /users/me should return 401 when not authenticated")
    void testMyProfile_Unauthorized() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    } */

    /* @Test
    @DisplayName("GET /users/me/roles should return current user roles")
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    void testMyRoles() throws Exception {
        mockMvc.perform(get("/users/me/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    } */

    @Test
    @DisplayName("GET /users should return all users for SUPER_ADMIN")
    @WithMockUser(username = "admin@example.com", roles = {"SUPER_ADMIN"})
    void testListUsers_AsAdmin() throws Exception {
        when(userService.findAllUsers()).thenReturn(List.of(userResponse));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].email").value("test@example.com"));
    }

    @Test
    @DisplayName("GET /users should return 403 for non-admin users")
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    void testListUsers_AsUser() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users/{id} should return user by id for SUPER_ADMIN")
    @WithMockUser(username = "admin@example.com", roles = {"SUPER_ADMIN"})
    void testFindUserById_AsAdmin() throws Exception {
        when(userService.findUserById(1L)).thenReturn(userResponse);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("GET /users/{id} should return 403 for non-admin users")
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    void testFindUserById_AsUser() throws Exception {
        mockMvc.perform(get("/users/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users/roles should return all roles for SUPER_ADMIN")
    @WithMockUser(username = "admin@example.com", roles = {"SUPER_ADMIN"})
    void testGetRoles_AsAdmin() throws Exception {
        RoleResponse roleResponse = new RoleResponse(1L, "USER", "Basic user role");
        when(userService.getAvailableRoles()).thenReturn(List.of(roleResponse));

        mockMvc.perform(get("/users/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("USER"));
    }

    @Test
    @DisplayName("POST /users/{id}/roles should assign role for SUPER_ADMIN")
    @WithMockUser(username = "admin@example.com", roles = {"SUPER_ADMIN"})
    void testAssignRole_AsAdmin() throws Exception {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setRoleName("SUPER_ADMIN");
        request.setAirlineId(null);
        request.setHotelId(null);

        UserResponse updatedResponse = new UserResponse(1L, "Test", "User", "test@example.com", "SUPER_ADMIN");
        when(userService.assignRoleToUser(eq(1L), any(AssignRoleRequest.class))).thenReturn(updatedResponse);

        mockMvc.perform(post("/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
    }

    @Test
    @DisplayName("POST /users/{id}/roles should return 403 for non-admin users")
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    void testAssignRole_AsUser() throws Exception {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setRoleName("SUPER_ADMIN");

        mockMvc.perform(post("/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /users/{id}/roles/{roleId} should remove role for SUPER_ADMIN")
    @WithMockUser(username = "admin@example.com", roles = {"SUPER_ADMIN"})
    void testRemoveRole_AsAdmin() throws Exception {
        when(userService.removeRoleFromUser(1L, 2L)).thenReturn(userResponse);

        mockMvc.perform(delete("/users/1/roles/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("DELETE /users/{id}/roles/{roleId} should return 403 for non-admin users")
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    void testRemoveRole_AsUser() throws Exception {
        mockMvc.perform(delete("/users/1/roles/2"))
                .andExpect(status().isForbidden());
    }
}
