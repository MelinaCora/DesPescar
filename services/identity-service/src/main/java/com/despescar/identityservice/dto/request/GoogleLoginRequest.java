package com.despescar.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** ID token (credential) que devuelve el boton de Google Identity Services en el navegador. */
@Getter
@Setter
public class GoogleLoginRequest {

    @NotBlank(message = "Falta la credencial de Google")
    private String credential;
}
