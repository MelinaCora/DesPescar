package com.despescar.reservationservice.dto.reservation.request;

import com.despescar.reservationservice.enums.PaymentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/** Parte de vuelo que entra al carrito (POST /init, contrato C4). */
@Data
public class BookingInitRequest {

    /** Ida y, opcionalmente, vuelta. */
    @NotEmpty
    @Size(max = 2)
    private List<UUID> flightIds;

    @NotNull
    @Min(1)
    @Max(9)
    private Integer cantidadPasajeros;

    @NotNull
    private PaymentType paymentType;

    /** En desuso desde E2: se ignora (las estadías entran por POST /carrito/estadias). */
    private UUID hotelId;

    private Long packageId;

    /** Ids de las tarifas elegidas, una por tramo y en el mismo orden que flightIds. */
    @NotEmpty
    @Size(max = 2)
    private List<UUID> baggageIds;
}
