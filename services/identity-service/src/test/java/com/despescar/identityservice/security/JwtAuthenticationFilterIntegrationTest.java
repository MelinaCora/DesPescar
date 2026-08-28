package com.despescar.identityservice.security;

import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.entity.UserRole;
import com.despescar.identityservice.repository.UserRepository;
import com.despescar.identityservice.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("JWT Authentication Filter Integration Tests")
class JwtAuthenticationFilterIntegrationTest {

    private static final String TEST_SECRET = "test-secret-key-for-ci-identity-service-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserService userService;

    private User superAdminUser;

    @BeforeEach
    void setUp() {
        superAdminUser = new User();
        superAdminUser.setId(1L);
        superAdminUser.setEmail("admin@example.com");
        superAdminUser.setFirstName("Admin");
        superAdminUser.setLastName("User");

        Role superAdminRole = new Role();
        superAdminRole.setId(1L);
        superAdminRole.setName("SUPER_ADMIN");

        UserRole assignment = new UserRole();
        assignment.setRole(superAdminRole);
        superAdminUser.addRole(assignment);
    }

    @Test
    @DisplayName("Access token should allow access to protected endpoints")
    void testAccessTokenAllowsProtectedEndpoint() throws Exception {
        String accessToken = jwtService.generateToken("admin@example.com", "SUPER_ADMIN");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(superAdminUser));
        when(userService.findAllUsers()).thenReturn(List.of(
                new UserResponse(1L, "Admin", "User", "admin@example.com", "SUPER_ADMIN")
        ));

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Expired access token should be rejected")
    void testExpiredAccessTokenRejected() throws Exception {
        JwtService shortLivedJwtService = new JwtService(TEST_SECRET, 1L);
        String expiredAccessToken = shortLivedJwtService.generateToken("admin@example.com", "SUPER_ADMIN");
        Thread.sleep(10);

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + expiredAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Refresh token cannot be used as access token")
    void testRefreshTokenCannotBeUsedAsAccessToken() throws Exception {
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer not-a-jwt-refresh-token"))
                .andExpect(status().isForbidden());
    }
}
