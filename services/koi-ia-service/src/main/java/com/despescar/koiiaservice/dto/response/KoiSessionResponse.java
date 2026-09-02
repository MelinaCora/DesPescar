package com.despescar.koiiaservice.dto.response;

import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.UserIntent;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class KoiSessionResponse {

    private UUID sessionId;
    private ConversationStage stage;
    private UserIntent intent;
    private MissingInfoField awaitingField;
    private BigDecimal budget;
    private Integer travelers;
    private String destination;
    private String travelMonth;
    private String userGoal;
    private String lastAssistantMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
