package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class MercadoPagoWebhookServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @Mock
    private PaymentHistoryService paymentHistoryService;

    @InjectMocks
    private MercadoPagoWebhookService mercadoPagoWebhookService;

    @Test
    void processPaymentNotificationShouldApprovePaymentFromExternalReference() {
        Payment payment = payment();
        String mercadoPagoPaymentId = "mp-789";

        when(paymentGatewayService.getPaymentStatus(mercadoPagoPaymentId))
                .thenReturn(PaymentGatewayResponse.builder()
                        .approved(true)
                        .transactionId(mercadoPagoPaymentId)
                        .externalReference(payment.getId().toString())
                        .status("approved")
                        .message("approved")
                        .build());
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        mercadoPagoWebhookService.processPaymentNotification(mercadoPagoPaymentId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).isEqualTo(mercadoPagoPaymentId);
        assertThat(payment.getPaymentDate()).isNotNull();
        verify(paymentHistoryService).saveHistory(payment, PaymentStatus.APPROVED, "Payment status updated from Mercado Pago.");
    }

    @Test
    void processPaymentNotificationShouldSkipPersistenceWhenStatusDidNotChange() {
        Payment payment = payment();
        payment.setStatus(PaymentStatus.APPROVED);
        String mercadoPagoPaymentId = "mp-789";

        when(paymentGatewayService.getPaymentStatus(mercadoPagoPaymentId))
                .thenReturn(PaymentGatewayResponse.builder()
                        .approved(true)
                        .transactionId(mercadoPagoPaymentId)
                        .externalReference(payment.getId().toString())
                        .status("approved")
                        .message("approved")
                        .build());
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        mercadoPagoWebhookService.processPaymentNotification(mercadoPagoPaymentId);

        verify(paymentRepository, never()).save(any());
        verify(paymentHistoryService, never()).saveHistory(any(), any(), any());
    }

    private Payment payment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .reservationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("1250.00"))
                .status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("ARS")
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
