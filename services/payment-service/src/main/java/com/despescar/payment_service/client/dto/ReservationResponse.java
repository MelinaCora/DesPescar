package com.despescar.payment_service.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Lo que payment-service usa del carrito de reservation-service (contrato C3,
 * GET /api/bookings/internal/{id}). El resto de los campos se ignora.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReservationResponse {

    private Long idCarrito;
    private Long creadorId;
    /** INICIADA | PENDIENTE_PAGO | ESPERANDO_PAGADORES | CONFIRMADA | EXPIRADA | CANCELADA */
    private String estadoGeneral;
    private Long segundosRestantes;
    private BigDecimal montoTotal;
    private String moneda;
}
