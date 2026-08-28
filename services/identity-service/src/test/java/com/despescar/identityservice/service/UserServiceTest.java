package com.despescar.identityservice.service;

import com.despescar.identityservice.dto.request.AssignRoleRequest;
import com.despescar.identityservice.dto.request.RegisterUserRequest;
import com.despescar.identityservice.dto.response.RoleResponse;
import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.entity.UserRole;
import com.despescar.identityservice.exception.EmailAlreadyExistsException;
import com.despescar.identityservice.exception.RoleNotFoundException;
import com.despescar.identityservice.mapper.UserMapper;
import com.despescar.identityservice.repository.RoleRepository;
import com.despescar.identityservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User Service Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterUserRequest registerRequest;
    private User testUser;
    private Role userRole;
    private Role superAdminRole;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterUserRequest();
        registerRequest.setFirstName("Test");
        registerRequest.setLastName("User");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        userRole = new Role();
        userRole.setId(1L);
        userRole.setName("USER");

        superAdminRole = new Role();
        superAdminRole.setId(2L);
        superAdminRole.setName("SUPER_ADMIN");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail(registerRequest.getEmail());
        testUser.setFirstName(registerRequest.getFirstName());
        testUser.setLastName(registerRequest.getLastName());
    }

    @Test
    @DisplayName("registerUser should create new user with USER role")
    void testRegisterUser_Success() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(roleRepository.findByNameIgnoreCase("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(new UserResponse(1L, "Test", "User", "test@example.com", "USER"));

        UserResponse response = userService.registerUser(registerRequest);

        assertNotNull(response);
        verify(userRepository).existsByEmail(registerRequest.getEmail());
        verify(roleRepository).findByNameIgnoreCase("USER");
        verify(passwordEncoder).encode(registerRequest.getPassword());
        
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertEquals(registerRequest.getFirstName(), savedUser.getFirstName());
        assertEquals(registerRequest.getLastName(), savedUser.getLastName());
        assertEquals(registerRequest.getEmail(), savedUser.getEmail());
        assertTrue(savedUser.getIsActive());
        assertFalse(savedUser.getRoles().isEmpty());
    }

    @Test
    @DisplayName("registerUser should throw exception when email already exists")
    void testRegisterUser_EmailExists() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> {
            userService.registerUser(registerRequest);
        });

        verify(userRepository).existsByEmail(registerRequest.getEmail());
        verify(roleRepository, never()).findByNameIgnoreCase(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser should throw exception when USER role not found")
    void testRegisterUser_RoleNotFound() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(roleRepository.findByNameIgnoreCase("USER")).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () -> {
            userService.registerUser(registerRequest);
        });

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("findUserById should return user when found")
    void testFindUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(new UserResponse(1L, "Test", "User", "test@example.com", "USER"));

        UserResponse response = userService.findUserById(1L);

        assertNotNull(response);
        verify(userRepository).findById(1L);
        verify(userMapper).toResponse(testUser);
    }

    @Test
    @DisplayName("findUserById should throw exception when not found")
    void testFindUserById_NotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            userService.findUserById(1L);
        });

        verify(userRepository).findById(1L);
        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    @DisplayName("findAllUsers should return all users")
    void testFindAllUsers() {
        List<User> users = List.of(testUser);
        when(userRepository.findAll()).thenReturn(users);
        when(userMapper.toResponse(any(User.class)))
                .thenReturn(new UserResponse(1L, "Test", "User", "test@example.com", "USER"));

        List<UserResponse> responses = userService.findAllUsers();

        assertNotNull(responses);
        assertEquals(1, responses.size());
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("assignRoleToUser should assign role successfully")
    void testAssignRoleToUser_Success() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setRoleName("SUPER_ADMIN");
        request.setAirlineId(null);
        request.setHotelId(null);

        UserRole existingRole = new UserRole();
        existingRole.setRole(userRole);
        testUser.addRole(existingRole);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByNameIgnoreCase("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(new UserResponse(1L, "Test", "User", "test@example.com", "SUPER_ADMIN"));

        UserResponse response = userService.assignRoleToUser(1L, request);

        assertNotNull(response);
        verify(userRepository).findById(1L);
        verify(roleRepository).findByNameIgnoreCase("SUPER_ADMIN");
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("assignRoleToUser should not duplicate existing role")
    void testAssignRoleToUser_AlreadyAssigned() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setRoleName("USER");
        request.setAirlineId(null);
        request.setHotelId(null);

        UserRole existingRole = new UserRole();
        existingRole.setRole(userRole);
        existingRole.setAirlineId(null);
        existingRole.setHotelId(null);
        testUser.addRole(existingRole);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByNameIgnoreCase("USER")).thenReturn(Optional.of(userRole));
        when(userMapper.toResponse(testUser)).thenReturn(new UserResponse(1L, "Test", "User", "test@example.com", "USER"));

        UserResponse response = userService.assignRoleToUser(1L, request);

        assertNotNull(response);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("assignRoleToUser should throw exception when user not found")
    void testAssignRoleToUser_UserNotFound() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setRoleName("SUPER_ADMIN");

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            userService.assignRoleToUser(1L, request);
        });

        verify(roleRepository, never()).findByNameIgnoreCase(anyString());
    }

    @Test
    @DisplayName("assignRoleToUser should throw exception when role not found")
    void testAssignRoleToUser_RoleNotFound() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setRoleName("INVALID_ROLE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByNameIgnoreCase("INVALID_ROLE")).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () -> {
            userService.assignRoleToUser(1L, request);
        });

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("removeRoleFromUser should remove role successfully")
    void testRemoveRoleFromUser_Success() {
        UserRole ur = new UserRole();
        ur.setRole(userRole);
        testUser.addRole(ur);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(userRole));
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(new UserResponse(1L, "Test", "User", "test@example.com", "USER"));

        UserResponse response = userService.removeRoleFromUser(1L, 1L);

        assertNotNull(response);
        verify(userRepository).findById(1L);
        verify(roleRepository).findById(1L);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("removeRoleFromUser should throw exception when user not found")
    void testRemoveRoleFromUser_UserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            userService.removeRoleFromUser(1L, 1L);
        });

        verify(roleRepository, never()).findById(any());
    }

    @Test
    @DisplayName("removeRoleFromUser should throw exception when role not found")
    void testRemoveRoleFromUser_RoleNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () -> {
            userService.removeRoleFromUser(1L, 1L);
        });

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("getAvailableRoles should return all roles")
    void testGetAvailableRoles() {
        List<Role> roles = List.of(userRole, superAdminRole);
        when(roleRepository.findAll()).thenReturn(roles);

        List<RoleResponse> responses = userService.getAvailableRoles();

        assertNotNull(responses);
        assertEquals(2, responses.size());
        verify(roleRepository).findAll();
    }
}
