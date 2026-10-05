package com.despescar.payment_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.service.MercadoPagoGatewayService;
import com.despescar.payment_service.service.MockPaymentGatewayService;
import com.despescar.payment_service.service.PaymentGatewayService;

class PaymentProviderMercadoPagoContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockPaymentGatewayService.class, MercadoPagoGatewayService.class);

    @Test
    void conProviderMercadoPagoUsaMercadoPago() {
        runner.withPropertyValues("payments.provider=mercadopago", "mercadopago.access-token=TEST-123")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PaymentGatewayService.class)
                            .doesNotHaveBean(MockPaymentGatewayService.class);
                    assertThat(ctx.getBean(PaymentGatewayService.class).provider())
                            .isEqualTo(PaymentProvider.MERCADO_PAGO);
                });
    }

    @Test
    void mercadoPagoSinAccessTokenNoArranca() {
        runner.withPropertyValues("payments.provider=mercadopago", "mercadopago.access-token=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
