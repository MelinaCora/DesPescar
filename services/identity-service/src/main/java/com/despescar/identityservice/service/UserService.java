package com.despescar.identityservice.service;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import com.despescar.identityservice.dto.request.RegisterUserRequest;
import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.exception.EmailAlreadyExistsException;
import com.despescar.identityservice.exception.RoleNotFoundException;
import com.despescar.identityservice.mapper.UserMapper;
import com.despescar.identityservice.repository.RoleRepository;
import com.despescar.identityservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

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

	    if(userRepository.existsByEmail(request.getEmail())) {
	        throw new EmailAlreadyExistsException();
	    }

	    Role role = roleRepository.findByName("CLIENT")
	            .orElseThrow(RoleNotFoundException::new);

	    User user = new User();

	    user.setFirstName(request.getFirstName());
	    user.setLastName(request.getLastName());
	    user.setEmail(request.getEmail());
	    user.setPassword(
	            passwordEncoder.encode(request.getPassword())
	    );
	    user.setRegistrationDate(LocalDate.now());
	    user.setIsActive(true);
	    user.setRole(role);

	    User savedUser = userRepository.save(user);

	    return userMapper.toResponse(savedUser);
	}

}