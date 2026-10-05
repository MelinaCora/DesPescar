package com.despescar.payment_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.service.MercadoPagoGatewayService;
import com.despescar.payment_service.service.MockPaymentGatewayService;
import com.despescar.payment_service.service.PaymentGatewayService;

class PaymentProviderMockContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockPaymentGatewayService.class, MercadoPagoGatewayService.class);

    @Test
    void sinConfigurarUsaElMock() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(PaymentGatewayService.class);
            assertThat(ctx.getBean(PaymentGatewayService.class).provider()).isEqualTo(PaymentProvider.MOCK);
        });
    }

    @Test
    void conProviderMockUsaElMockAunqueHayaTokenDeMercadoPago() {
        runner.withPropertyValues("payments.provider=mock", "mercadopago.access-token=TEST-123")
                .run(ctx -> assertThat(ctx).hasSingleBean(MockPaymentGatewayService.class)
                        .doesNotHaveBean(MercadoPagoGatewayService.class));
    }
}
