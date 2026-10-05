package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentProvider;

class MockPaymentGatewayServiceTest {

    private final MockPaymentGatewayService gateway = new MockPaymentGatewayService();

    @Test
    void elCheckoutEsLaPaginaDelSimuladorEnElFront() {
        PaymentCheckoutResponse checkout = gateway.createCheckout("abc-123", new BigDecimal("1060000.00"), "ARS");

        assertThat(checkout.getCheckoutUrl()).isEqualTo("/pago/simulado?pago=abc-123");
        assertThat(checkout.getPreferenceId()).isEqualTo("MOCK-PREF-abc-123");
    }

    @Test
    void rechazaMontosNoPositivos() {
        assertThatThrownBy(() -> gateway.createCheckout("abc", BigDecimal.ZERO, "ARS"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulaElReembolso() {
        RefundGatewayResponse ok = gateway.refund("MOCK-abc", new BigDecimal("10.00"));
        RefundGatewayResponse sinTransaccion = gateway.refund(" ", new BigDecimal("10.00"));

        assertThat(ok.isApproved()).isTrue();
        assertThat(ok.getRefundTransactionId()).startsWith("MOCK-REFUND-");
        assertThat(sinTransaccion.isApproved()).isFalse();
    }

    @Test
    void informaQueEsElProveedorMock() {
        assertThat(gateway.provider()).isEqualTo(PaymentProvider.MOCK);
    }
}
