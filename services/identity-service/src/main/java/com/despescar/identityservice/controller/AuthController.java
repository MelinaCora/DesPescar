package com.despescar.identityservice.controller;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.request.RegisterUserRequest;
import com.despescar.identityservice.dto.response.CurrentUserResponse;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.service.AuthService;
import com.despescar.identityservice.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacion y registro.
 *
 * <p>La ruta /me devuelve el usuario autenticado y su rol principal. Los roles
 * adicionales y sus alcances pueden consultarse desde UserRole.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return userService.registerUser(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return new CurrentUserResponse(user.getEmail(), user.getPrimaryRoleName());
    }
}