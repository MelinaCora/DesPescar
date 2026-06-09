package com.despescar.identityservice.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.despescar.identityservice.dto.request.RegisterUserRequest;
import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.service.UserService;
import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.dto.response.CurrentUserResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {
	
	private final UserService userService;
	private final AuthService authService;
	
	public AuthController(
	        UserService userService,
	        AuthService authService) {

	    this.userService = userService;
	    this.authService = authService;
	}
	
	@PostMapping("/register")
	public UserResponse register(@RequestBody RegisterUserRequest request) {

	    return userService.registerUser(request);
	}
	
	@PostMapping("/login")
	public LoginResponse login(
	        @RequestBody LoginRequest request) {

	    return authService.login(request);
	}
	
	@GetMapping("/me")
	public CurrentUserResponse me(
	        Authentication authentication
	) {

	    User user =
	            (User) authentication.getPrincipal();

	    return new CurrentUserResponse(

	            user.getEmail(),

	            user.getRole().getName()
	    );
	}


}
