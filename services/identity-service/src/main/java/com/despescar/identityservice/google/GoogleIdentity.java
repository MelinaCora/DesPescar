package com.despescar.identityservice.google;

/** Datos del usuario que Google confirma en el ID token. */
public record GoogleIdentity(String email, String firstName, String lastName) {
}
