package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

    @Mock
    private AprobacionPagoService aprobacionPagoService;

    @InjectMocks
    private MercadoPagoWebhookService service;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = Payment.builder()
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

    private void mercadoPagoInforma(String status, String externalReference) {
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        when(paymentGatewayService.getPaymentStatus("445")).thenReturn(PaymentGatewayResponse.builder()
                .approved("approved".equals(status))
                .transactionId("445")
                .externalReference(externalReference)
                .status(status)
                .amount(new BigDecimal("1250.00"))
                .paymentTypeId("credit_card")
                .build());
    }

    private void pagoEncontrado() {
        when(paymentRepository.findByIdParaActualizar(payment.getId())).thenReturn(Optional.of(payment));
    }

    @Test
    void approvedApruebaElPagoConElMontoDeMercadoPago() {
        mercadoPagoInforma("approved", payment.getId().toString());
        pagoEncontrado();

        service.processPaymentNotification("445", "payment");

        verify(aprobacionPagoService).aprobar(payment, "445", PaymentMethod.CREDIT_CARD, new BigDecimal("1250.00"));
    }

    @Test
    void rejectedMarcaElPagoSinTocarLaReserva() {
        mercadoPagoInforma("rejected", payment.getId().toString());
        pagoEncontrado();
        when(paymentRepository.save(payment)).thenReturn(payment);

        service.processPaymentNotification("445", "payment");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        verify(paymentHistoryService).saveHistory(payment, PaymentStatus.REJECTED, "Mercado Pago informo el pago como rejected.");
        verify(aprobacionPagoService, never()).aprobar(any(), any(), any(), any());
    }

    @Test
    void unRechazoNoPisaUnPagoYaAprobado() {
        payment.setStatus(PaymentStatus.APPROVED);
        mercadoPagoInforma("rejected", payment.getId().toString());
        pagoEncontrado();

        service.processPaymentNotification("445", "payment");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundedMarcaElPagoComoReembolsadoUnaSolaVez() {
        payment.setStatus(PaymentStatus.APPROVED);
        mercadoPagoInforma("refunded", payment.getId().toString());
        pagoEncontrado();
        when(paymentRepository.save(payment)).thenReturn(payment);

        service.processPaymentNotification("445", "payment");
        service.processPaymentNotification("445", "payment");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void ignoraReferenciasDesconocidasYOtrosTiposDeNotificacion() {
        mercadoPagoInforma("approved", "no-es-un-uuid");

        service.processPaymentNotification("445", "payment");
        service.processPaymentNotification("445", "merchant_order");

        verify(paymentRepository, never()).findByIdParaActualizar(any());
        verify(aprobacionPagoService, never()).aprobar(any(), any(), any(), any());
    }

    @Test
    void conElProveedorMockNoConsultaAMercadoPago() {
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MOCK);

        service.processPaymentNotification("445", "payment");

        verify(paymentGatewayService, never()).getPaymentStatus(any());
    }

    @Test
    void cancelledMarcaUnPagoPendiente() {
        mercadoPagoInforma("cancelled", payment.getId().toString());
        pagoEncontrado();
        when(paymentRepository.save(payment)).thenReturn(payment);

        service.processPaymentNotification("445", "payment");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentHistoryService).saveHistory(payment, PaymentStatus.CANCELLED, "Mercado Pago informo el pago como cancelled.");
    }

    @Test
    void refundedSobreUnPagoPendienteSinTransaccionLoMarcaReembolsado() {
        mercadoPagoInforma("refunded", payment.getId().toString());
        pagoEncontrado();
        when(paymentRepository.save(payment)).thenReturn(payment);

        service.processPaymentNotification("445", "payment");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundedDeOtroCobroNoCambiaElPago() {
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setTransactionId("111");
        mercadoPagoInforma("refunded", payment.getId().toString());
        pagoEncontrado();

        service.processPaymentNotification("445", "payment");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void unSegundoCobroAprobadoDeLaMismaPreferenciaSeReembolsa() {
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setTransactionId("111");
        mercadoPagoInforma("approved", payment.getId().toString());
        pagoEncontrado();

        service.processPaymentNotification("445", "payment");

        verify(aprobacionPagoService).reembolsarCobroDuplicado(eq(payment), eq("445"), eq(new BigDecimal("1250.00")), any());
        verify(aprobacionPagoService, never()).aprobar(any(), any(), any(), any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void elMismoCobroAprobadoDosVecesNoSeReembolsa() {
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setTransactionId("445");
        mercadoPagoInforma("approved", payment.getId().toString());
        pagoEncontrado();

        service.processPaymentNotification("445", "payment");

        verify(aprobacionPagoService, never()).reembolsarCobroDuplicado(any(), any(), any(), any());
    }

    @Test
    void unCobroAprobadoEnOtraMonedaNoSeApruebaAutomaticamente() {
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        when(paymentGatewayService.getPaymentStatus("445")).thenReturn(PaymentGatewayResponse.builder()
                .approved(true).transactionId("445").externalReference(payment.getId().toString())
                .status("approved").currency("USD").amount(new BigDecimal("1250.00")).build());
        pagoEncontrado();

        service.processPaymentNotification("445", "payment");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentHistoryService).saveHistory(eq(payment), eq(PaymentStatus.PENDING),
                startsWith("Revision manual: moneda"));
        verify(aprobacionPagoService, never()).aprobar(any(), any(), any(), any());
    }

    @Test
    void laRevisionManualDeMonedaNoSeRepiteParaElMismoCobro() {
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        when(paymentGatewayService.getPaymentStatus("445")).thenReturn(PaymentGatewayResponse.builder()
                .approved(true).transactionId("445").externalReference(payment.getId().toString())
                .status("approved").currency("USD").amount(new BigDecimal("1250.00")).build());
        pagoEncontrado();
        when(paymentHistoryService.existe(eq(payment.getId()), startsWith("Revision manual: moneda"))).thenReturn(true);

        service.processPaymentNotification("445", "payment");

        verify(paymentHistoryService, never()).saveHistory(any(), any(), any());
    }
}
