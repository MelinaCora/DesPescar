package com.despescar.koiiaservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class KoiConversationMessageRequest {

    @NotBlank
    private String message;

    // Historial reciente que manda React para darle memoria al bot
    private List<MensajeHistorialDto> history;

    @Data
    public static class MensajeHistorialDto {
        private String role;    // "user" o "assistant"
        private String content; // El texto del mensaje
    }
}