package com.despescar.identityservice.dto.response;

public class CurrentUserResponse {

    private String email;
    private String role;

    public CurrentUserResponse(
            String email,
            String role
    ) {
        this.email = email;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}