package com.despescar.identityservice.dto.response;

/** Lo que el front necesita para mostrar el boton de Google: el client id publico, o null si no esta configurado. */
public record GoogleConfigResponse(String clientId) {
}
