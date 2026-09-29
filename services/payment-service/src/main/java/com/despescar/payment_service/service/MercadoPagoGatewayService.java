package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.resources.payment.Payment;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.preference.Preference;

@Service
public class MercadoPagoGatewayService implements PaymentGatewayService {

    private final String notificationUrl;
    private final String successUrl;
    private final String pendingUrl;
    private final String failureUrl;

    public MercadoPagoGatewayService(
            @Value("${mercadopago.access-token}") String accessToken,
            @Value("${mercadopago.notification-url:}") String notificationUrl,
            @Value("${mercadopago.checkout.success-url:}") String successUrl,
            @Value("${mercadopago.checkout.pending-url:}") String pendingUrl,
            @Value("${mercadopago.checkout.failure-url:}") String failureUrl) {

        MercadoPagoConfig.setAccessToken(accessToken);
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

        try {

            PreferenceItemRequest item =
                    PreferenceItemRequest.builder()
                            .title("DesPescar - Reserva")
                            .quantity(1)
                            .currencyId(currency)
                            .unitPrice(amount)
                            .build();

            PreferenceRequest.PreferenceRequestBuilder preferenceRequestBuilder =
                    PreferenceRequest.builder()
                            .items(List.of(item))
                            .externalReference(paymentId);

            PreferenceBackUrlsRequest backUrls = buildBackUrls();
            if (backUrls != null) {
                preferenceRequestBuilder.backUrls(backUrls);
            }
            if (hasText(notificationUrl)) {
                preferenceRequestBuilder.notificationUrl(notificationUrl);
            }

            PreferenceRequest preferenceRequest = preferenceRequestBuilder.build();

            PreferenceClient client = new PreferenceClient();

            Preference preference =
                    client.create(preferenceRequest);

            return PaymentCheckoutResponse.builder()
                    .preferenceId(preference.getId())
                    .checkoutUrl(preference.getInitPoint())
                    .message("Checkout created successfully.")
                    .build();

        } catch (Exception ex) {

            throw new RuntimeException(
                    "Error creating Mercado Pago checkout.",
                    ex
            );
        }
    }



    @Override
    public PaymentGatewayResponse getPaymentStatus(
            String transactionId) {

        try {

            PaymentClient client = new PaymentClient();

            Payment payment =
                    client.get(Long.valueOf(transactionId));

            boolean approved =
                    "approved".equalsIgnoreCase(
                            payment.getStatus()
                    );

            return PaymentGatewayResponse.builder()
                    .approved(approved)
                    .transactionId(
                            payment.getId().toString()
                    )
                    .externalReference(payment.getExternalReference())
                    .status(payment.getStatus())
                    .currency(payment.getCurrencyId())
                    .paymentTypeId(payment.getPaymentTypeId())
                    .paymentMethodId(payment.getPaymentMethodId())
                    .message(payment.getStatusDetail())
                    .build();

        } catch (Exception ex) {

            throw new RuntimeException(
                    "Error retrieving Mercado Pago payment status.",
                    ex
            );
        }
    }


    @Override
    public RefundGatewayResponse refund(
            String transactionId,
            BigDecimal amount) {

        throw new UnsupportedOperationException(
                "Refund not implemented yet."
        );
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
