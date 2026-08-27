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
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de gestion de usuarios.
 *
 * <p>Todo usuario nuevo se registra con el rol basico USER. Los roles
 * administrativos se asignan por separado y pueden tener alcance por aerolinea o
 * hotel, segun lo definido en UserRole.</p>
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse registerUser(RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException();
        }

        Role defaultRole = roleRepository.findByNameIgnoreCase("USER")
                .orElseThrow(RoleNotFoundException::new);

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRegistrationDate(LocalDate.now());
        user.setIsActive(true);

        UserRole userRole = new UserRole();
        userRole.setRole(defaultRole);
        user.addRole(userRole);

        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    public UserResponse findUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return userMapper.toResponse(user);
    }

    public List<UserResponse> findAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    public List<RoleResponse> getAvailableRoles() {
        return roleRepository.findAll().stream()
                .map(role -> new RoleResponse(role.getId(), role.getName(), role.getDescription()))
                .toList();
    }

    @Transactional
    public UserResponse assignRoleToUser(Long userId, AssignRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Role role = roleRepository.findByNameIgnoreCase(request.getRoleName())
                .orElseThrow(RoleNotFoundException::new);

        boolean alreadyAssigned = user.getRoles().stream()
                .anyMatch(userRole -> userRole.getRole() != null
                        && userRole.getRole().getId().equals(role.getId())
                        && java.util.Objects.equals(userRole.getAirlineId(), request.getAirlineId())
                        && java.util.Objects.equals(userRole.getHotelId(), request.getHotelId()));

        if (alreadyAssigned) {
            return userMapper.toResponse(user);
        }

        UserRole userRole = new UserRole();
        userRole.setRole(role);
        userRole.setAirlineId(request.getAirlineId());
        userRole.setHotelId(request.getHotelId());
        user.addRole(userRole);

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse removeRoleFromUser(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(RoleNotFoundException::new);

        user.getRoles().removeIf(userRole ->
                userRole.getRole() != null
                        && userRole.getRole().getId().equals(role.getId()));

        userRepository.save(user);
        return userMapper.toResponse(user);
    }
}
