package com.despescar.identityservice.service;

import org.springframework.stereotype.Service;
import com.despescar.identityservice.repository.RoleRepository;
import com.despescar.identityservice.repository.UserRepository;

@Service
public class UserService {
	
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	    
	public UserService(UserRepository userRepository,RoleRepository roleRepository) {

		 this.userRepository = userRepository;
		 this.roleRepository = roleRepository;
	}

}