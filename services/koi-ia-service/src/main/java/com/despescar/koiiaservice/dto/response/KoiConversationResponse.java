package com.despescar.koiiaservice.dto.response;

import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.UserIntent;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class KoiConversationResponse {

    private UUID sessionId;
    private String reply;
    private boolean needsMoreInfo;
    private MissingInfoField nextQuestion;
    private List<MissingInfoField> missingFields;
    private UserIntent intent;
    private ConversationStage stage;
    private List<KoiRecommendationResponse> recommendations;
}
