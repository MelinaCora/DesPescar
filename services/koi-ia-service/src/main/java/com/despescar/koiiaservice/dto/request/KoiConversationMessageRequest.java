package com.despescar.koiiaservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Mensaje del usuario. El historial ya no viaja desde el cliente (se lee del servidor); si un
 * cliente viejo manda "history", se ignora.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KoiConversationMessageRequest {

    @NotBlank
    @Size(max = 1000)
    private String message;
}
