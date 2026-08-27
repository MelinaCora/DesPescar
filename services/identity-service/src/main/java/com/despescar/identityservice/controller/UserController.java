package com.despescar.identityservice.controller;

import com.despescar.identityservice.dto.request.AssignRoleRequest;
import com.despescar.identityservice.dto.response.RoleResponse;
import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador para consultar y administrar usuarios y roles.
 *
 * <p>Este endpoint se encarga de la parte de identidad y permisos del sistema.
 * La autenticacion publica queda en AuthController, mientras que este modulo
 * protege operaciones de administracion y consulta de usuarios.</p>
 */
@RestController
@RequestMapping("/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> myProfile(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPrimaryRoleName()
        ));
    }

    @GetMapping("/me/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> myRoles(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<String> roles = user.getRoles().stream()
                .filter(userRole -> userRole.getRole() != null)
                .map(userRole -> userRole.getRole().getName())
                .toList();
        return ResponseEntity.ok(roles);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> findUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<RoleResponse>> roles() {
        return ResponseEntity.ok(userService.getAvailableRoles());
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> assignRoleToUser(
            @PathVariable Long id,
            @RequestBody AssignRoleRequest request
    ) {
        return ResponseEntity.ok(userService.assignRoleToUser(id, request));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> removeRoleFromUser(
            @PathVariable Long id,
            @PathVariable Long roleId
    ) {
        return ResponseEntity.ok(userService.removeRoleFromUser(id, roleId));
    }
}
