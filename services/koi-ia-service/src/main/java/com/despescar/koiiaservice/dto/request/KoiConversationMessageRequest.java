package com.despescar.koiiaservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KoiConversationMessageRequest {

    @NotBlank
    private String message;
}
