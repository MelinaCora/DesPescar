package com.despescar.reservationservice.dto.reservation.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SeatResponse {
    private String seatNumber;
    private UUID seatUuid;
    private String seatStatus;
    private Long blockedByUserId;
}
