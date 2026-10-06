package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.exception.ProveedorPagoException;
import com.despescar.payment_service.service.MercadoPagoOrdenGateway.CrearOrden;

class MercadoPagoOrdersGatewayServiceTest {

    private static final String BASE = "https://mp.test";
    private static final String TOKEN = "tok-secreto-1234567890abcdef";
    private static final String PAGO_ID = "0b3f0a2e-1111-4222-8333-444455556666";

    private MockRestServiceServer servidor;
    private MercadoPagoOrdersGatewayService gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        gateway = new MercadoPagoOrdersGatewayService("APP_USR-acceso", "APP_USR-publica", BASE, builder);
    }

    private static String orden(String id, String status, String detail, String pagoStatus, String pagoDetail) {
        return """
                {"id":"%s","type":"online","status":"%s","status_detail":"%s","external_reference":"%s",
                 "total_amount":"1250.00","total_paid_amount":"1250.00",
                 "transactions":{"payments":[{"id":"PAY01","amount":"1250.00","status":"%s","status_detail":"%s",
                   "payment_method":{"id":"master","type":"credit_card","token":"%s","installments":1}}]}}
                """.formatted(id, status, detail, PAGO_ID, pagoStatus, pagoDetail, TOKEN);
    }

    private CrearOrden pedido() {
        return new CrearOrden(PAGO_ID, new BigDecimal("1250"), TOKEN, "master", "credit_card", 1, "comprador@testuser.com");
    }

    @Test
    void sinCredencialesNoArranca() {
        assertThatThrownBy(() -> new MercadoPagoOrdersGatewayService("", "pk", BASE, RestClient.builder()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MERCADOPAGO_ACCESS_TOKEN");
        assertThatThrownBy(() -> new MercadoPagoOrdersGatewayService("tok", "", BASE, RestClient.builder()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MERCADOPAGO_PUBLIC_KEY");
    }

    @Test
    void elCheckoutEsLaPaginaPropiaYNoLlamaAMercadoPago() {
        PaymentCheckoutResponse checkout = gateway.createCheckout(PAGO_ID, new BigDecimal("1250.00"), "ARS");

        assertThat(checkout.getCheckoutUrl()).isEqualTo("/pago/mercadopago?pago=" + PAGO_ID);
        assertThat(checkout.getPreferenceId()).isNull();
        assertThat(gateway.provider()).isEqualTo(PaymentProvider.MERCADO_PAGO_ORDERS);
        assertThat(gateway.publicKey()).isEqualTo("APP_USR-publica");
        servidor.verify();
    }

    @Test
    void creaLaOrdenConElCuerpoYLosHeadersQuePideMercadoPago() {
        servidor.expect(requestTo(BASE + "/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer APP_USR-acceso"))
                .andExpect(header("X-Idempotency-Key", MercadoPagoOrdersGatewayService.claveDeOrden(PAGO_ID, TOKEN)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("online"))
                .andExpect(jsonPath("$.processing_mode").value("automatic"))
                .andExpect(jsonPath("$.total_amount").value("1250.00"))
                .andExpect(jsonPath("$.external_reference").value(PAGO_ID))
                .andExpect(jsonPath("$.payer.email").value("comprador@testuser.com"))
                .andExpect(jsonPath("$.transactions.payments[0].amount").value("1250.00"))
                .andExpect(jsonPath("$.transactions.payments[0].payment_method.id").value("master"))
                .andExpect(jsonPath("$.transactions.payments[0].payment_method.type").value("credit_card"))
                .andExpect(jsonPath("$.transactions.payments[0].payment_method.token").value(TOKEN))
                .andExpect(jsonPath("$.transactions.payments[0].payment_method.installments").value(1))
                .andExpect(jsonPath("$.transactions.payments[0].payment_method.statement_descriptor").value("DESPESCAR"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(orden("ORD01", "processed", "accredited", "processed", "accredited")));

        OrdenMercadoPago orden = gateway.crearOrden(pedido());

        servidor.verify();
        assertThat(orden.id()).isEqualTo("ORD01");
        assertThat(orden.aprobada()).isTrue();
        assertThat(orden.rechazada()).isFalse();
        PaymentGatewayResponse r = orden.toGatewayResponse();
        assertThat(r.getStatus()).isEqualTo("approved");
        assertThat(r.getTransactionId()).isEqualTo("ORD01");
        assertThat(r.getExternalReference()).isEqualTo(PAGO_ID);
        assertThat(r.getAmount()).isEqualByComparingTo("1250.00");
        assertThat(r.getPaymentTypeId()).isEqualTo("credit_card");
        assertThat(r.getPaymentMethodId()).isEqualTo("master");
        assertThat(r.getCurrency()).isEqualTo("ARS");
    }

    @Test
    void laClaveDeIdempotenciaEsFijaPorPagoYTokenYNoContieneElToken() {
        String clave = MercadoPagoOrdersGatewayService.claveDeOrden(PAGO_ID, TOKEN);

        assertThat(clave).isEqualTo(MercadoPagoOrdersGatewayService.claveDeOrden(PAGO_ID, TOKEN))
                .doesNotContain(TOKEN).hasSizeLessThanOrEqualTo(128).startsWith("despescar-orden-" + PAGO_ID);
        assertThat(MercadoPagoOrdersGatewayService.claveDeOrden(PAGO_ID, "otro-token")).isNotEqualTo(clave);
    }

    /** El 402 real de Mercado Pago: la orden viene anidada en "data" junto a la lista de errores. */
    private static String rechazoReal(String ordenJson) {
        return """
                {"errors":[{"code":"failed","message":"The following transactions failed",
                  "details":["PAY01: insufficient_amount"]}],"data":%s}
                """.formatted(ordenJson);
    }

    @Test
    void unaTarjetaRechazadaLlegaComo402YSeDevuelveComoOrdenFallida() {
        servidor.expect(requestTo(BASE + "/v1/orders"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                        .body(rechazoReal(orden("ORD02", "failed", "failed", "failed", "insufficient_amount"))));

        OrdenMercadoPago orden = gateway.crearOrden(pedido());

        assertThat(orden.rechazada()).isTrue();
        assertThat(orden.detalle()).isEqualTo("insufficient_amount");
        assertThat(orden.toGatewayResponse().getStatus()).isEqualTo("rejected");
        assertThat(orden.toGatewayResponse().getMessage()).isEqualTo("insufficient_amount");
    }

    @Test
    void elRechazoConLaOrdenEnElNivelSuperiorTambienSeEntiende() {
        servidor.expect(requestTo(BASE + "/v1/orders"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                        .body(orden("ORD04", "failed", "failed", "failed", "rejected_by_issuer")));

        OrdenMercadoPago orden = gateway.crearOrden(pedido());

        assertThat(orden.id()).isEqualTo("ORD04");
        assertThat(orden.rechazada()).isTrue();
        assertThat(orden.detalle()).isEqualTo("rejected_by_issuer");
    }

    @Test
    void unaOrdenEnProcesoQuedaPendiente() {
        servidor.expect(requestTo(BASE + "/v1/orders"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(orden("ORD03", "processing", "in_process", "processing", "in_process")));

        OrdenMercadoPago orden = gateway.crearOrden(pedido());

        assertThat(orden.aprobada()).isFalse();
        assertThat(orden.rechazada()).isFalse();
        assertThat(orden.toGatewayResponse().getStatus()).isEqualTo("in_process");
    }

    @Test
    void unErrorDeMercadoPagoNoRepiteElTokenNiElCuerpo() {
        servidor.expect(requestTo(BASE + "/v1/orders"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"invalid_email_for_sandbox\",\"message\":\"token " + TOKEN + "\"}]}"));

        assertThatThrownBy(() -> gateway.crearOrden(pedido()))
                .isInstanceOf(ProveedorPagoException.class)
                .hasMessageContaining("HTTP 400").hasMessageContaining("invalid_email_for_sandbox")
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).doesNotContain(TOKEN);
                    assertThat(ex.getCause()).isNull();
                });
    }

    @Test
    void consultaLaOrdenYLaTraduceAlEstadoDelPago() {
        servidor.expect(requestTo(BASE + "/v1/orders/ORD01"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer APP_USR-acceso"))
                .andRespond(withSuccess(orden("ORD01", "refunded", "refunded", "refunded", "refunded"),
                        MediaType.APPLICATION_JSON));

        PaymentGatewayResponse r = gateway.getPaymentStatus("ORD01");

        assertThat(r.getStatus()).isEqualTo("refunded");
        assertThat(r.isApproved()).isFalse();
    }

    @Test
    void elIdDeOrdenInvalidoNoSaleALaRed() {
        assertThatThrownBy(() -> gateway.consultarOrden("ORD01/../x")).isInstanceOf(ProveedorPagoException.class);
        servidor.verify();
    }

    @Test
    void reembolsaElTotalConCuerpoVacioYClaveFija() {
        servidor.expect(requestTo(BASE + "/v1/orders/ORD01"))
                .andRespond(withSuccess(orden("ORD01", "processed", "accredited", "processed", "accredited"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/v1/orders/ORD01/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "despescar-reembolso-ORD01-125000"))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"id":"ORD01","status":"refunded","status_detail":"refunded",
                                 "transactions":{"refunds":[{"id":"REF01","transaction_id":"PAY01","amount":"1250.00","status":"processed"}]}}
                                """));

        RefundGatewayResponse r = gateway.refund("ORD01", new BigDecimal("1250.00"));

        servidor.verify();
        assertThat(r.isApproved()).isTrue();
        assertThat(r.getRefundTransactionId()).isEqualTo("REF01");
    }

    @Test
    void reembolsaParcialmenteIndicandoLaTransaccionYElMonto() {
        servidor.expect(requestTo(BASE + "/v1/orders/ORD01"))
                .andRespond(withSuccess(orden("ORD01", "processed", "accredited", "processed", "accredited"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/v1/orders/ORD01/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "despescar-reembolso-ORD01-50000"))
                .andExpect(jsonPath("$.transactions[0].id").value("PAY01"))
                .andExpect(jsonPath("$.transactions[0].amount").value("500.00"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"id":"ORD01","status":"processed","status_detail":"partially_refunded",
                                 "transactions":{"refunds":[{"id":"REF02","transaction_id":"PAY01","amount":"500.00","status":"processed"}]}}
                                """));

        RefundGatewayResponse r = gateway.refund("ORD01", new BigDecimal("500"));

        servidor.verify();
        assertThat(r.isApproved()).isTrue();
        assertThat(r.getRefundTransactionId()).isEqualTo("REF02");
        assertThat(r.getMessage()).contains("parcial");
    }

    @Test
    void unReembolsoRechazadoNoLanzaYQuedaComoManualPendiente() {
        servidor.expect(requestTo(BASE + "/v1/orders/ORD01"))
                .andRespond(withSuccess(orden("ORD01", "refunded", "refunded", "refunded", "refunded"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/v1/orders/ORD01/refund"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"order_already_refunded\"}]}"));

        RefundGatewayResponse r = gateway.refund("ORD01", new BigDecimal("1250.00"));

        assertThat(r.isApproved()).isFalse();
        assertThat(r.getMessage()).contains("HTTP 409").contains("order_already_refunded");
    }

    @Test
    void unReembolsoConIdOMontoInvalidoNoSaleALaRed() {
        assertThat(gateway.refund("123 456", new BigDecimal("10")).isApproved()).isFalse();
        assertThat(gateway.refund("ORD01", BigDecimal.ZERO).isApproved()).isFalse();
        servidor.verify();
    }
}
