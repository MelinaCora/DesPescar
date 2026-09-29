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

import com.despescar.payment_service.client.ReservationClient;
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

    @Mock
    private ReservationClient reservationClient;

    @InjectMocks
    private MercadoPagoWebhookService mercadoPagoWebhookService;

    @Test
    void processPaymentNotificationShouldApprovePaymentAndSyncReservation() {
        Payment payment = payment();
        String mercadoPagoPaymentId = "445";

        when(paymentGatewayService.getPaymentStatus(mercadoPagoPaymentId))
                .thenReturn(PaymentGatewayResponse.builder()
                        .approved(true)
                        .transactionId(mercadoPagoPaymentId)
                        .externalReference(payment.getId().toString())
                        .status("approved")
                        .paymentTypeId("credit_card")
                        .message("approved")
                        .build());
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        mercadoPagoWebhookService.processPaymentNotification(mercadoPagoPaymentId, "payment");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).isEqualTo(mercadoPagoPaymentId);
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(payment.getPaymentDate()).isNotNull();
        verify(reservationClient).markReservationPaymentPaid(payment.getReservationId(), payment.getUserId(), mercadoPagoPaymentId);
        verify(paymentHistoryService).saveHistory(payment, PaymentStatus.APPROVED, "Payment status updated from Mercado Pago.");
    }

    @Test
    void processPaymentNotificationShouldSkipPersistenceWhenStatusDidNotChange() {
        Payment payment = payment();
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setTransactionId("445");
        payment.setPaymentMethod(PaymentMethod.CREDIT_CARD);

        when(paymentGatewayService.getPaymentStatus("445"))
                .thenReturn(PaymentGatewayResponse.builder()
                        .approved(true)
                        .transactionId("445")
                        .externalReference(payment.getId().toString())
                        .status("approved")
                        .paymentTypeId("credit_card")
                        .message("approved")
                        .build());
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        mercadoPagoWebhookService.processPaymentNotification("445", "payment");

        verify(paymentRepository, never()).save(any());
        verify(paymentHistoryService, never()).saveHistory(any(), any(), any());
        verify(reservationClient, never()).markReservationPaymentPaid(any(), any(), any());
    }

    @Test
    void processPaymentNotificationShouldIgnoreNonPaymentNotifications() {
        mercadoPagoWebhookService.processPaymentNotification("445", "merchant_order");

        verify(paymentGatewayService, never()).getPaymentStatus(any());
    }

    private Payment payment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .reservationId(77L)
                .userId(55L)
                .amount(new BigDecimal("1250.00"))
                .status(PaymentStatus.PENDING)
                .currency("ARS")
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
