package com.despescar.payment_service.dto.response;

import java.math.BigDecimal;

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
public class PaymentGatewayResponse {

    private boolean approved;

    private String transactionId;

    private String externalReference;

    private String status;

    private String currency;

    /** Monto cobrado por el proveedor (Mercado Pago: transaction_amount). */
    private BigDecimal amount;

    private String paymentTypeId;

    private String paymentMethodId;

    private String message;
}
