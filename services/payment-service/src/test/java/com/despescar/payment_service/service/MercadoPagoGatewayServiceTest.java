package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.exception.ProveedorPagoException;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.net.MPResponse;
import com.mercadopago.resources.preference.Preference;

class MercadoPagoGatewayServiceTest {

    /** Doble que reemplaza la llamada HTTP a Mercado Pago. */
    private static class MercadoPagoDeMentira extends MercadoPagoGatewayService {
        private final List<String> llamadas = new ArrayList<>();
        private final String estado;
        private final boolean falla;
        private String monedaDelPago = "ARS";
        private BigDecimal precioPreferencia;

        MercadoPagoDeMentira(String estado, boolean falla) {
            super("TEST-token", "", "", "", "", 3000, 5000);
            this.estado = estado;
            this.falla = falla;
        }

        @Override
        protected String monedaDelPago(long mpPaymentId) {
            return monedaDelPago;
        }

        @Override
        protected Preference crearPreferencia(PreferenceRequest request) {
            precioPreferencia = request.getItems().get(0).getUnitPrice();
            Preference p = new Preference();
            return p;
        }

        @Override
        protected ReembolsoMercadoPago pedirReembolso(long mpPaymentId, BigDecimal monto, String claveIdempotencia)
                throws MPApiException {
            llamadas.add(mpPaymentId + "|" + monto + "|" + claveIdempotencia);
            if (falla) {
                throw new MPApiException("rechazado", new MPResponse(400, null, "{}"));
            }
            return new ReembolsoMercadoPago("9001", estado);
        }
    }

    @Test
    void reembolsaElTotalConUnaClaveDeIdempotenciaPorPago() {
        MercadoPagoDeMentira gateway = new MercadoPagoDeMentira("approved", false);

        RefundGatewayResponse r = gateway.refund("123456", new BigDecimal("1060000.00"));

        assertThat(r.isApproved()).isTrue();
        assertThat(r.getRefundTransactionId()).isEqualTo("9001");
        assertThat(gateway.llamadas).containsExactly("123456|1060000.00|despescar-reembolso-123456");
    }

    @Test
    void unReembolsoNoAprobadoOConErrorNoSeDaPorHecho() {
        RefundGatewayResponse enProceso = new MercadoPagoDeMentira("in_process", false)
                .refund("123456", new BigDecimal("10.00"));
        RefundGatewayResponse conError = new MercadoPagoDeMentira("approved", true)
                .refund("123456", new BigDecimal("10.00"));

        assertThat(enProceso.isApproved()).isFalse();
        assertThat(enProceso.getMessage()).contains("in_process");
        assertThat(conError.isApproved()).isFalse();
        assertThat(conError.getMessage()).contains("400");
    }

    @Test
    void sinIdNumericoDeMercadoPagoNoLlama() {
        MercadoPagoDeMentira gateway = new MercadoPagoDeMentira("approved", false);

        RefundGatewayResponse r = gateway.refund("MOCK-abc", new BigDecimal("10.00"));

        assertThat(r.isApproved()).isFalse();
        assertThat(gateway.llamadas).isEmpty();
    }

    @Test
    void sinAccessTokenNoArranca() {
        assertThatThrownBy(() -> new MercadoPagoGatewayService(" ", "", "", "", "", 3000, 5000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MERCADOPAGO_ACCESS_TOKEN");
    }

    @Test
    void informaQueEsMercadoPago() {
        assertThat(new MercadoPagoDeMentira("approved", false).provider()).isEqualTo(PaymentProvider.MERCADO_PAGO);
    }

    @Test
    void elReembolsoSeMandaConEscala2() {
        MercadoPagoDeMentira gateway = new MercadoPagoDeMentira("approved", false);

        gateway.refund("123456", new BigDecimal("10"));

        assertThat(gateway.llamadas).containsExactly("123456|10.00|despescar-reembolso-123456");
    }

    @Test
    void noReembolsaUnPagoQueNoEsEnPesos() {
        MercadoPagoDeMentira gateway = new MercadoPagoDeMentira("approved", false);
        gateway.monedaDelPago = "USD";

        RefundGatewayResponse r = gateway.refund("123456", new BigDecimal("10.00"));

        assertThat(r.isApproved()).isFalse();
        assertThat(gateway.llamadas).isEmpty();
    }

    @Test
    void elCheckoutNormalizaElMontoYRechazaOtraMoneda() {
        MercadoPagoDeMentira gateway = new MercadoPagoDeMentira("approved", false);

        gateway.createCheckout("p1", new BigDecimal("1250.5"), "ARS");

        assertThat(gateway.precioPreferencia).isEqualTo(new BigDecimal("1250.50"));
        assertThatThrownBy(() -> gateway.createCheckout("p1", new BigDecimal("10.00"), "USD"))
                .isInstanceOf(ProveedorPagoException.class);
    }

    @Test
    void elMensajeDeUnReembolsoFallidoNoFiltraElMensajeDeLaExcepcion() {
        MercadoPagoDeMentira gateway = new MercadoPagoDeMentira("approved", false) {
            @Override
            protected ReembolsoMercadoPago pedirReembolso(long id, BigDecimal monto, String clave) {
                throw new IllegalStateException("token=SECRETO");
            }
        };

        RefundGatewayResponse r = gateway.refund("123456", new BigDecimal("10.00"));

        assertThat(r.getMessage()).contains("IllegalStateException").doesNotContain("SECRETO");
    }
}
