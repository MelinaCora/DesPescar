package com.despescar.identityservice.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.despescar.identityservice.dto.request.LoginRequest;
import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.exception.InvalidCredentialsException;
import com.despescar.identityservice.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(
            LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(
                        InvalidCredentialsException::new
                );

        boolean validPassword =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                );

        if (!validPassword) {

            throw new InvalidCredentialsException();
        }

        return new LoginResponse(
                "LOGIN_OK"
        );
    }
}