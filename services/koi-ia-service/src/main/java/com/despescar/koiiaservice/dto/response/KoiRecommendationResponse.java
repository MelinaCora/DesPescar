package com.despescar.koiiaservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class KoiRecommendationResponse {

    private String type;
    private String title;
    private String summary;
    private BigDecimal price;
    private String destination;
    private String flightNumber;
    private UUID hotelId;
    private String hotelName;
    private String hotelCity;
    private Integer hotelStars;
    private Double hotelPricePerNight;
    private Integer availableSeats;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer durationNights;
    private String whyItFits;
}
