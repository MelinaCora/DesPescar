package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ConfirmacionReservaResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.ReservationClientException;
import com.despescar.payment_service.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class AprobacionPagoServiceTest {

    private static final BigDecimal TOTAL = new BigDecimal("1060000.00");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentHistoryService paymentHistoryService;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private ReservationClient reservationClient;

    @InjectMocks
    private AprobacionPagoService service;

    private Payment pago;

    @BeforeEach
    void setUp() {
        pago = Payment.builder()
                .id(UUID.randomUUID())
                .reservationId(12L)
                .userId(7L)
                .amount(TOTAL)
                .status(PaymentStatus.PENDING)
                .currency("ARS")
                .provider(PaymentProvider.MOCK)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void guarda() {
        when(paymentRepository.save(pago)).thenReturn(pago);
    }

    private void reservaResponde(String estado, String motivo) {
        when(reservationClient.confirmarPago(12L, 7L, "MOCK-1", TOTAL))
                .thenReturn(new ConfirmacionReservaResponse(estado, motivo, "mensaje de la reserva"));
    }

    @Test
    void apruebaYConfirmaLaReserva() {
        guarda();
        reservaResponde("CONFIRMADA", null);

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(r.getTransactionId()).isEqualTo("MOCK-1");
        assertThat(r.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(r.getPaymentDate()).isNotNull();
        verify(paymentHistoryService).saveHistory(pago, PaymentStatus.APPROVED, "Pago aprobado por MOCK.");
        verify(paymentGatewayService, never()).refund(any(), any());
    }

    @Test
    void siLaReservaNoSeConfirmaReembolsaElTotal() {
        guarda();
        reservaResponde("CANCELADA", "PAGO_TARDIO_SIN_DISPONIBILIDAD");
        when(paymentGatewayService.refund("MOCK-1", TOTAL))
                .thenReturn(RefundGatewayResponse.builder().approved(true).refundTransactionId("R1").build());

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentHistoryService).saveHistory(eq(pago), eq(PaymentStatus.REFUNDED),
                startsWith("Reembolso automatico (PAGO_TARDIO_SIN_DISPONIBILIDAD)"));
    }

    @Test
    void siElReembolsoNoSaleQuedaAprobadoConReembolsoManualPendiente() {
        guarda();
        reservaResponde("RECHAZADA", "MONTO_NO_COINCIDE");
        when(paymentGatewayService.refund("MOCK-1", TOTAL))
                .thenReturn(RefundGatewayResponse.builder().approved(false).message("HTTP 400").build());

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentHistoryService).saveHistory(pago, PaymentStatus.APPROVED,
                "Reembolso manual pendiente (MONTO_NO_COINCIDE): HTTP 400");
    }

    @Test
    void siElProveedorLanzaAlReembolsarTambienQuedaPendienteManual() {
        guarda();
        reservaResponde("CANCELADA", "RESERVA_CANCELADA");
        when(paymentGatewayService.refund("MOCK-1", TOTAL)).thenThrow(new IllegalStateException("caido"));

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentHistoryService).saveHistory(pago, PaymentStatus.APPROVED,
                "Reembolso manual pendiente (RESERVA_CANCELADA): IllegalStateException");
    }

    @Test
    void mandaAReservasElMontoQueInformaElProveedorConEscala2() {
        BigDecimal cobrado = new BigDecimal("480000.00");
        guarda();
        when(reservationClient.confirmarPago(12L, 7L, "MOCK-1", cobrado))
                .thenReturn(new ConfirmacionReservaResponse("RECHAZADA", "MONTO_NO_COINCIDE", "x"));
        when(paymentGatewayService.refund("MOCK-1", cobrado))
                .thenReturn(RefundGatewayResponse.builder().approved(true).build());

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, new BigDecimal("480000"));

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void unPagoViejoAprobadoConReembolsoPendienteNoImpideConfirmarElCorrecto() {
        Payment viejo = Payment.builder().id(UUID.randomUUID()).reservationId(12L).userId(7L)
                .amount(TOTAL).status(PaymentStatus.APPROVED).provider(PaymentProvider.MOCK).build();
        lenient().when(paymentRepository.findByReservationId(12L)).thenReturn(List.of(viejo, pago));
        guarda();
        reservaResponde("CONFIRMADA", null);

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(reservationClient).confirmarPago(12L, 7L, "MOCK-1", TOTAL);
        verify(paymentGatewayService, never()).refund(any(), any());
    }

    @Test
    void siReservasRespondePagoDuplicadoSeReembolsa() {
        guarda();
        reservaResponde("RECHAZADA", "PAGO_DUPLICADO");
        when(paymentGatewayService.refund("MOCK-1", TOTAL))
                .thenReturn(RefundGatewayResponse.builder().approved(true).build());

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentHistoryService).saveHistory(eq(pago), eq(PaymentStatus.REFUNDED),
                startsWith("Reembolso automatico (PAGO_DUPLICADO)"));
    }

    private void reservaResponde(String token, String estado, String motivo) {
        when(reservationClient.confirmarPago(12L, 7L, token, TOTAL))
                .thenReturn(new ConfirmacionReservaResponse(estado, motivo, "mensaje de la reserva"));
    }

    @Test
    void unCobroDuplicadoQueLaReservaRechazaSeReembolsaSinTocarElPago() {
        pago.setStatus(PaymentStatus.APPROVED);
        pago.setTransactionId("445");
        reservaResponde("999", "RECHAZADA", "PAGO_DUPLICADO");
        when(paymentGatewayService.refund("999", TOTAL))
                .thenReturn(RefundGatewayResponse.builder().approved(true).build());

        service.reembolsarCobroDuplicado(pago, "999", new BigDecimal("1060000"));

        assertThat(pago.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(pago.getTransactionId()).isEqualTo("445");
        verify(paymentHistoryService).saveHistory(eq(pago), eq(PaymentStatus.APPROVED),
                startsWith("Cobro duplicado 999 reembolsado (PAGO_DUPLICADO)"));
    }

    @Test
    void siElReembolsoDelCobroDuplicadoNoSaleQuedaPendienteManual() {
        pago.setStatus(PaymentStatus.APPROVED);
        pago.setTransactionId("445");
        reservaResponde("999", "RECHAZADA", "PAGO_DUPLICADO");
        when(paymentGatewayService.refund("999", TOTAL))
                .thenReturn(RefundGatewayResponse.builder().approved(false).message("HTTP 400").build());

        service.reembolsarCobroDuplicado(pago, "999", TOTAL);

        verify(paymentHistoryService).saveHistory(pago, PaymentStatus.APPROVED,
                "Reembolso manual pendiente del cobro duplicado 999 (PAGO_DUPLICADO): HTTP 400");
    }

    @Test
    void siElOtroCobroEraElQueConfirmoLaReservaSeQuedaConEseYYaEstabaReembolsadoElAnterior() {
        // A aprobo pero reservas tardo; B llego primero, fue duplicado y se reembolso (el pago quedo REFUNDED con B).
        pago.setStatus(PaymentStatus.REFUNDED);
        pago.setTransactionId("B");
        reservaResponde("A", "CONFIRMADA", null);
        when(paymentRepository.save(pago)).thenReturn(pago);

        service.reembolsarCobroDuplicado(pago, "A", TOTAL);

        assertThat(pago.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(pago.getTransactionId()).isEqualTo("A");
        verify(paymentGatewayService, never()).refund(any(), any());
        verify(paymentHistoryService).saveHistory(eq(pago), eq(PaymentStatus.APPROVED), startsWith("El cobro A confirmo la reserva"));
    }

    @Test
    void siElAnteriorNoSeHabiaReembolsadoSeReembolsaElAnteriorYNoElReal() {
        pago.setStatus(PaymentStatus.APPROVED);
        pago.setTransactionId("B");
        reservaResponde("A", "CONFIRMADA", null);
        when(paymentRepository.save(pago)).thenReturn(pago);
        when(paymentGatewayService.refund("B", TOTAL))
                .thenReturn(RefundGatewayResponse.builder().approved(true).build());

        service.reembolsarCobroDuplicado(pago, "A", TOTAL);

        assertThat(pago.getTransactionId()).isEqualTo("A");
        assertThat(pago.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentGatewayService).refund("B", TOTAL);
        verify(paymentGatewayService, never()).refund(eq("A"), any());
    }

    @Test
    void siReservasNoRespondeAlVerificarElOtroCobroNoSeReembolsaNada() {
        pago.setStatus(PaymentStatus.APPROVED);
        pago.setTransactionId("B");
        when(reservationClient.confirmarPago(12L, 7L, "A", TOTAL)).thenThrow(new ReservationClientException("caido"));

        assertThatThrownBy(() -> service.reembolsarCobroDuplicado(pago, "A", TOTAL))
                .isInstanceOf(ReservationClientException.class);
        verify(paymentGatewayService, never()).refund(any(), any());
    }

    @Test
    void unPagoYaAprobadoOReembolsadoNoSeVuelveAProcesar() {
        pago.setStatus(PaymentStatus.APPROVED);
        service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);
        pago.setStatus(PaymentStatus.REFUNDED);
        service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        verifyNoInteractions(reservationClient, paymentGatewayService, paymentRepository, paymentHistoryService);
    }

    @Test
    void siReservasNoRespondeLanzaYNoReembolsa() {
        guarda();
        when(reservationClient.confirmarPago(12L, 7L, "MOCK-1", TOTAL))
                .thenThrow(new ReservationClientException("caido"));

        assertThatThrownBy(() -> service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null))
                .isInstanceOf(ReservationClientException.class);
        verify(paymentGatewayService, never()).refund(any(), any());
    }
}
