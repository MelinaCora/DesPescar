package com.despescar.payment_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.service.MercadoPagoGatewayService;
import com.despescar.payment_service.service.MercadoPagoOrdenGateway;
import com.despescar.payment_service.service.MercadoPagoOrdersGatewayService;
import com.despescar.payment_service.service.MockPaymentGatewayService;
import com.despescar.payment_service.service.PaymentGatewayService;

class PaymentProviderMercadoPagoOrdersContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockPaymentGatewayService.class, MercadoPagoGatewayService.class,
                    MercadoPagoOrdersGatewayService.class);

    @Test
    void conProviderMercadoPagoOrdersUsaLaPasarelaDeOrdenes() {
        runner.withPropertyValues("payments.provider=mercadopago_orders",
                        "mercadopago.access-token=APP_USR-acceso", "mercadopago.public-key=APP_USR-publica")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PaymentGatewayService.class)
                            .hasSingleBean(MercadoPagoOrdenGateway.class)
                            .doesNotHaveBean(MockPaymentGatewayService.class)
                            .doesNotHaveBean(MercadoPagoGatewayService.class);
                    assertThat(ctx.getBean(PaymentGatewayService.class).provider())
                            .isEqualTo(PaymentProvider.MERCADO_PAGO_ORDERS);
                    assertThat(ctx.getBean(MercadoPagoOrdenGateway.class).publicKey()).isEqualTo("APP_USR-publica");
                });
    }

    @Test
    void sinAccessTokenOSinPublicKeyNoArranca() {
        runner.withPropertyValues("payments.provider=mercadopago_orders",
                        "mercadopago.access-token=", "mercadopago.public-key=APP_USR-publica")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("payments.provider=mercadopago_orders",
                        "mercadopago.access-token=APP_USR-acceso", "mercadopago.public-key=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void conMockOCheckoutProNoHayPasarelaDeOrdenes() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(MercadoPagoOrdenGateway.class)
                .hasSingleBean(MockPaymentGatewayService.class));
        runner.withPropertyValues("payments.provider=mercadopago", "mercadopago.access-token=TEST-123")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MercadoPagoOrdenGateway.class));
    }
}
