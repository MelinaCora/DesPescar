package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.enums.PaymentMethod;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.preference.Preference;

@Service
public class MercadoPagoGatewayService implements PaymentGatewayService {

    public MercadoPagoGatewayService(
            @Value("${mercadopago.access-token}") String accessToken) {

        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Override
    public PaymentCheckoutResponse createCheckout(
            String paymentId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod) {

        try {

            PreferenceItemRequest item =
                    PreferenceItemRequest.builder()
                            .title("DesPescar - Reserva")
                            .quantity(1)
                            .currencyId(currency)
                            .unitPrice(amount)
                            .build();

            PreferenceRequest preferenceRequest =
                    PreferenceRequest.builder()
                            .items(List.of(item))
                            .externalReference(paymentId)
                            .build();

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
                    .status(payment.getStatus())
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
}

