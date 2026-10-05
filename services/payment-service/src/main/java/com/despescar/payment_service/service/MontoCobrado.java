package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.entity.Payment;

import lombok.extern.slf4j.Slf4j;

/**
 * Lo que el proveedor cobró de verdad en un cobro, que es lo que hay que devolver al reembolsarlo:
 * puede no coincidir con el monto del pago. Si no se puede consultar, se usa el monto del pago.
 */
@Slf4j
final class MontoCobrado {

    private MontoCobrado() {
    }

    /** Siempre con escala 2 (Mercado Pago puede informar 1060000 sin decimales). */
    static BigDecimal de(PaymentGatewayService paymentGatewayService, Payment payment, String transactionId) {
        BigDecimal monto = null;
        try {
            PaymentGatewayResponse cobro = paymentGatewayService.getPaymentStatus(transactionId);
            monto = cobro == null ? null : cobro.getAmount();
        } catch (RuntimeException ex) {
            log.warn("No se pudo consultar el monto del cobro {}: {}", transactionId, ex.getMessage());
        }
        return (monto != null ? monto : payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
    }
}
