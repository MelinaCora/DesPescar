package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.exception.ProveedorPagoException;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentRefund;
import com.mercadopago.resources.preference.Preference;

/**
 * Mercado Pago Checkout Pro (payments.provider=mercadopago). Sin auto_return: con back_urls en
 * localhost Mercado Pago puede rechazar la preferencia (D17).
 */
@Service
@ConditionalOnProperty(name = "payments.provider", havingValue = "mercadopago")
public class MercadoPagoGatewayService implements PaymentGatewayService {

    private static final String MONEDA = "ARS";

    private final String notificationUrl;
    private final String successUrl;
    private final String pendingUrl;
    private final String failureUrl;

    public MercadoPagoGatewayService(
            @Value("${mercadopago.access-token:}") String accessToken,
            @Value("${mercadopago.notification-url:}") String notificationUrl,
            @Value("${mercadopago.checkout.success-url:}") String successUrl,
            @Value("${mercadopago.checkout.pending-url:}") String pendingUrl,
            @Value("${mercadopago.checkout.failure-url:}") String failureUrl,
            @Value("${mercadopago.connection-timeout-ms:5000}") int connectionTimeoutMs,
            @Value("${mercadopago.socket-timeout-ms:15000}") int socketTimeoutMs) {

        if (!hasText(accessToken)) {
            throw new IllegalStateException(
                    "PAYMENT_PROVIDER=mercadopago necesita MERCADOPAGO_ACCESS_TOKEN (credenciales de prueba de Mercado Pago).");
        }
        MercadoPagoConfig.setAccessToken(accessToken);
        MercadoPagoConfig.setConnectionTimeout(connectionTimeoutMs);
        MercadoPagoConfig.setSocketTimeout(socketTimeoutMs);
        this.notificationUrl = notificationUrl;
        this.successUrl = successUrl;
        this.pendingUrl = pendingUrl;
        this.failureUrl = failureUrl;
    }

    @Override
    public PaymentCheckoutResponse createCheckout(
            String paymentId,
            BigDecimal amount,
            String currency) {

        if (!MONEDA.equalsIgnoreCase(currency)) {
            throw new ProveedorPagoException("Solo se cobra en pesos argentinos (ARS).", null);
        }
        BigDecimal monto = amount.setScale(2, RoundingMode.HALF_UP);

        try {
            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title("DesPescar - Reserva")
                    .quantity(1)
                    .currencyId(MONEDA)
                    .unitPrice(monto)
                    .build();

            PreferenceRequest.PreferenceRequestBuilder builder = PreferenceRequest.builder()
                    .items(List.of(item))
                    .externalReference(paymentId);

            PreferenceBackUrlsRequest backUrls = buildBackUrls();
            if (backUrls != null) {
                builder.backUrls(backUrls);
            }
            if (hasText(notificationUrl)) {
                builder.notificationUrl(notificationUrl);
            }

            Preference preference = crearPreferencia(builder.build());

            return PaymentCheckoutResponse.builder()
                    .preferenceId(preference.getId())
                    .checkoutUrl(preference.getInitPoint())
                    .message("Checkout created successfully.")
                    .build();
        } catch (MPApiException ex) {
            throw new ProveedorPagoException(
                    "Mercado Pago rechazo la preferencia (HTTP " + ex.getStatusCode() + ").", ex);
        } catch (MPException | RuntimeException ex) {
            throw new ProveedorPagoException("No se pudo crear el checkout de Mercado Pago.", ex);
        }
    }

    /** Llamada HTTP de la preferencia, separada para poder probar createCheckout sin red. */
    protected Preference crearPreferencia(PreferenceRequest request) throws MPException, MPApiException {
        return new PreferenceClient().create(request);
    }

    @Override
    public PaymentGatewayResponse getPaymentStatus(String transactionId) {

        try {
            Payment payment = new PaymentClient().get(Long.valueOf(transactionId));

            return PaymentGatewayResponse.builder()
                    .approved("approved".equalsIgnoreCase(payment.getStatus()))
                    .transactionId(payment.getId().toString())
                    .externalReference(payment.getExternalReference())
                    .status(payment.getStatus())
                    .currency(payment.getCurrencyId())
                    .amount(payment.getTransactionAmount())
                    .paymentTypeId(payment.getPaymentTypeId())
                    .paymentMethodId(payment.getPaymentMethodId())
                    .message(payment.getStatusDetail())
                    .build();
        } catch (MPApiException ex) {
            throw new ProveedorPagoException(
                    "Mercado Pago no devolvio el pago " + transactionId + " (HTTP " + ex.getStatusCode() + ").", ex);
        } catch (MPException | RuntimeException ex) {
            throw new ProveedorPagoException("No se pudo consultar el pago en Mercado Pago.", ex);
        }
    }

    /**
     * Reembolso total del pago en Mercado Pago. Nunca lanza: si Mercado Pago no lo aprueba
     * devuelve approved=false y quien llama lo registra como reembolso manual pendiente.
     */
    @Override
    public RefundGatewayResponse refund(String transactionId, BigDecimal amount) {

        if (transactionId == null || !transactionId.matches("\\d{1,19}")) {
            return rechazado("Refund rejected: invalid Mercado Pago payment id.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return rechazado("Refund rejected: invalid amount.");
        }

        BigDecimal monto = amount.setScale(2, RoundingMode.HALF_UP);

        try {
            long mpPaymentId = Long.parseLong(transactionId);
            if (!MONEDA.equalsIgnoreCase(monedaDelPago(mpPaymentId))) {
                return rechazado("Refund rejected: the payment is not in ARS.");
            }
            ReembolsoMercadoPago reembolso = pedirReembolso(
                    mpPaymentId, monto, "despescar-reembolso-" + transactionId);

            if (!"approved".equalsIgnoreCase(reembolso.status())) {
                return rechazado("Mercado Pago informo el reembolso en estado " + reembolso.status() + ".");
            }
            return RefundGatewayResponse.builder()
                    .approved(true)
                    .refundTransactionId(reembolso.id())
                    .message("Reembolso aprobado por Mercado Pago.")
                    .build();
        } catch (MPApiException ex) {
            return rechazado("Mercado Pago rechazo el reembolso (HTTP " + ex.getStatusCode() + ").");
        } catch (MPException | RuntimeException ex) {
            return rechazado("No se pudo pedir el reembolso a Mercado Pago (" + ex.getClass().getSimpleName() + ").");
        }
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MERCADO_PAGO;
    }

    /** Moneda del pago en Mercado Pago, separada para poder probar refund() sin red. */
    protected String monedaDelPago(long mpPaymentId) throws MPException, MPApiException {
        return new PaymentClient().get(mpPaymentId).getCurrencyId();
    }

    /**
     * Llamada HTTP del reembolso, separada para poder probar refund() sin red. La clave de
     * idempotencia es fija por pago: reintentar no reembolsa dos veces.
     */
    protected ReembolsoMercadoPago pedirReembolso(long mpPaymentId, BigDecimal monto, String claveIdempotencia)
            throws MPException, MPApiException {

        MPRequestOptions opciones = MPRequestOptions.builder()
                .customHeaders(Map.of("X-Idempotency-Key", claveIdempotencia))
                .build();
        PaymentRefund refund = new PaymentRefundClient().refund(mpPaymentId, monto, opciones);
        return new ReembolsoMercadoPago(
                refund.getId() == null ? null : refund.getId().toString(),
                refund.getStatus());
    }

    protected record ReembolsoMercadoPago(String id, String status) {
    }

    private RefundGatewayResponse rechazado(String mensaje) {
        return RefundGatewayResponse.builder()
                .approved(false)
                .refundTransactionId(null)
                .message(mensaje)
                .build();
    }

    private PreferenceBackUrlsRequest buildBackUrls() {
        if (!hasText(successUrl) && !hasText(pendingUrl) && !hasText(failureUrl)) {
            return null;
        }

        return PreferenceBackUrlsRequest.builder()
                .success(hasText(successUrl) ? successUrl : null)
                .pending(hasText(pendingUrl) ? pendingUrl : null)
                .failure(hasText(failureUrl) ? failureUrl : null)
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
