package com.despescar.identityservice.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.despescar.identityservice.dto.request.RegisterUserRequest;
import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.service.UserService;

@RestController
@RequestMapping("/auth")
public class AuthController {
	
	private final UserService userService;
	
	public AuthController(UserService userService) {
        this.userService = userService;
    }
	
	@PostMapping("/register")
	public UserResponse register(@RequestBody RegisterUserRequest request) {

	    return userService.registerUser(request);
	}


}
