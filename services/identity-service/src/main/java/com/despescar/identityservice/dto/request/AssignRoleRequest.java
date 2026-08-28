package com.despescar.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para asignar un rol a un usuario.
 *
 * <p>El alcance del rol puede quedar definido por airlineId o hotelId.
 * Un usuario puede tener un ADMIN de aerolinea para una aerolinea concreta,
 * o un ADMIN de hotel para un hotel especifico.</p>
 */
@Getter
@Setter
public class AssignRoleRequest {

    @NotBlank(message = "El nombre del rol es requerido")
    private String roleName;
    
    private Long airlineId;
    private Long hotelId;
}