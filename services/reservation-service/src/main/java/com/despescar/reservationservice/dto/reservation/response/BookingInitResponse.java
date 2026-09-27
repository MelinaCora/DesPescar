package com.despescar.reservationservice.dto.reservation.response;

import com.despescar.reservationservice.enums.PaymentType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookingInitResponse {
    private Long bookingId;
    private String status;
    private PaymentType paymentType;
}
