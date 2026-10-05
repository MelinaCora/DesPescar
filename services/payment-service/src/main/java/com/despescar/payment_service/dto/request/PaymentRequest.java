package com.despescar.payment_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "Reservation ID is required")
    private Long reservationId;

    /** Parte de un pago en grupo a cobrar (D-b9). Sin este campo se cobra el carrito completo. */
    @Min(value = 1, message = "La parte tiene que estar entre 1 y 10")
    @Max(value = 10, message = "La parte tiene que estar entre 1 y 10")
    private Integer parteNumero;

}
