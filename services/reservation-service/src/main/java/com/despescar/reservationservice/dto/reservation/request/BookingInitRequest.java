package com.despescar.reservationservice.dto.reservation.request;

import com.despescar.reservationservice.enums.PaymentType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class BookingInitRequest {
    @NotEmpty
    private List<UUID> flightIds;

    @NotNull
    private Integer cantidadPasajeros;

    @NotNull
    private PaymentType paymentType;

    private UUID hotelId;

    private Long packageId;

    private List<UUID> baggageIds;
}
