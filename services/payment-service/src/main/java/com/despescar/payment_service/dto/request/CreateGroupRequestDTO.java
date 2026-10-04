package com.despescar.payment_service.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateGroupRequestDTO {
    private Long reservationId;
    private BigDecimal totalAmount;
    private List<Long> userIds; // Los IDs de los amigos que van a dividir el pago
}