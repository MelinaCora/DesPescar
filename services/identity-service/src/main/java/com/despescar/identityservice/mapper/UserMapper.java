package com.despescar.identityservice.mapper;

import com.despescar.identityservice.dto.response.UserResponse;
import com.despescar.identityservice.entity.User;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformar entidades de dominio en respuestas de API.
 * En este servicio, cada usuario puede tener varios roles, por lo que se expone
 * su rol principal para mantener compatibilidad con los contratos actuales.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPrimaryRoleName()
        );
    }
}
