package com.despescar.koiiaservice.dto.response;

import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.UserIntent;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class KoiSessionResponse {

    private UUID sessionId;
    private ConversationStage stage;
    private UserIntent intent;
    private MissingInfoField awaitingField;
    private String userIdentifier;
    private BigDecimal budget;
    private Integer travelers;
    private String destination;
    private String origin;
    private String travelStyle;
    private Integer nights;
    private String travelMonth;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private BigDecimal preferredBudget;
    private Integer preferredTravelers;
    private String preferredDestination;
    private String preferredOrigin;
    private String preferredTravelStyle;
    private Integer preferredNights;
    private Boolean preferredSoloTravel;
    private LocalDate preferredDepartureDate;
    private LocalDate preferredReturnDate;
    private String userGoal;
    private String lastAssistantMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
