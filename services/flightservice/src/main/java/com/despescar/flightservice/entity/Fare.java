package com.despescar.flightservice.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "baggage_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fare {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    private boolean personalItem;
    private boolean carryOn;
    private boolean checkedBaggage;
    private boolean wifi;
    private String seatSelection;

    private String currency;
    private BigDecimal baseFare;
    private BigDecimal taxesAndFees;
    private BigDecimal transparentFinalPrice;
}