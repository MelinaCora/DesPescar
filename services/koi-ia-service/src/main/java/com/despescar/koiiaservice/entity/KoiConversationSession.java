package com.despescar.koiiaservice.entity;

import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.UserIntent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "koi_conversation_sessions")
public class KoiConversationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStage stage = ConversationStage.NEW;

    @Enumerated(EnumType.STRING)
    private UserIntent intent = UserIntent.UNKNOWN;

    @Enumerated(EnumType.STRING)
    private MissingInfoField awaitingField;

    private BigDecimal budget;

    private Integer travelers;

    private String destination;

    private String origin;

    private String travelStyle;

    private Integer nights;

    private String travelMonth;

    private LocalDate departureDate;

    private LocalDate returnDate;

    @Column(length = 500)
    private String userGoal;

    @Column(length = 120)
    private String userIdentifier;

    private BigDecimal preferredBudget;

    private Integer preferredTravelers;

    @Column(length = 120)
    private String preferredDestination;

    @Column(length = 120)
    private String preferredOrigin;

    @Column(length = 120)
    private String preferredTravelStyle;

    private Integer preferredNights;

    private Boolean preferredSoloTravel;

    private LocalDate preferredDepartureDate;

    private LocalDate preferredReturnDate;

    @Column(length = 2000)
    private String lastAssistantMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "session")
    private List<KoiConversationMessage> messages = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
