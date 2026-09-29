package com.despescar.payment_service.client.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationResponse {

    private Long idCarrito;
    private BigDecimal montoTotal;
    private String moneda;
    private List<SeatDetail> asientos;

    @Getter
    @Setter
    public static class SeatDetail {
        private Long pagadorId;
        private BigDecimal precioCobrado;
        private String estadoPago;
    }
}
