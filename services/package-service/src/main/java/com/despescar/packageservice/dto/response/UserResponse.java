package com.despescar.identityservice.dto.response;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;

@Getter
@Setter
@AllArgsConstructor

public class UserResponse {
	private Long id;

    private String firstName;

    private String lastName;

    private String email;

    private String role;
}
