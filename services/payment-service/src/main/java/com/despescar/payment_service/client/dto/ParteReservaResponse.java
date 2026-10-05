package com.despescar.payment_service.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Una parte de un pago en grupo tal como la informa reservation-service (contrato CB3,
 * GET /api/bookings/internal/{id}/partes/{numero}). usuarioId es null si la parte está LIBRE.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParteReservaResponse {

    private Long reservaId;
    private Integer numero;
    private Long usuarioId;
    private BigDecimal monto;
    private String moneda;
    /** LIBRE | TOMADA | PAGADA */
    private String estadoParte;
    /** ABIERTO | COMPLETO | CONFIRMADO | CANCELADO | VENCIDO */
    private String estadoGrupo;
    private Long segundosRestantes;
}
