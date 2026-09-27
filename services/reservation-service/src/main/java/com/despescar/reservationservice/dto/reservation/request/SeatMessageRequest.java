package com.despescar.reservationservice.dto.reservation.request;
import lombok.Data;
import java.util.UUID;

@Data
public class SeatMessageRequest {
    private UUID seatUuid;
    private Long userId;
}
