package com.despescar.identityservice.mapper;

import org.springframework.stereotype.Component;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.dto.response.UserResponse;

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