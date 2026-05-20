package com.despescar.identityservice.mapper;


import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {	
	
	
	public UserResponse toResponse(User user) {
		
		return new UserResponse(
				user.getId(),
				user.getFirstName(),
				user.getLastName(),
				user.getEmail(),
				user.getRole().getName()				
				);
	}
	
}


