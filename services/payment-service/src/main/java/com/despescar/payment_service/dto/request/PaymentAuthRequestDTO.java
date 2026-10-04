package com.despescar.payment_service.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class PaymentAuthRequestDTO {
    private UUID fractionId; // El ID de la tabla payment_fractions que le toca pagar
    private String token; // El token de la tarjeta generado por el SDK de MP en React
    private String paymentMethodId; // Ej: "visa", "master"
    private String issuerId; // ID del banco emisor
    private Integer installments; // Cuotas (usualmente 1 para autorizaciones)
    private String payerEmail; // Email del usuario que paga
}