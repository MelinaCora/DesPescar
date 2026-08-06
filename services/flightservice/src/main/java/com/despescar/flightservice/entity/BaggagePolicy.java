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
public class BaggagePolicy {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @Column(nullable = false)
    private Integer carryOnWeight;


    @Column(nullable = false)
    private Integer checkedBaggageWeight;


    @Column(nullable = false)
    private BigDecimal extraBaggagePrice;

}