package com.despescar.payment_service.dto.response;

import lombok.Data;

@Data
public class ReservationResponseDTO {
    private Long id;
    private Double totalAmount;
    private Integer passengersCount;
    private String status;
}