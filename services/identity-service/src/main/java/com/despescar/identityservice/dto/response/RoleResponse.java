package com.despescar.identityservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Respuesta de un rol disponible en el sistema.
 */
@Getter
@Setter
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private String description;
}
