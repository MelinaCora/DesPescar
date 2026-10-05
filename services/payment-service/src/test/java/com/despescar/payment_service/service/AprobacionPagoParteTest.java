package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

/** Aprobación de un pago de parte (D-b10): se confirma la parte, no la reserva entera. */
@ExtendWith(MockitoExtension.class)
class AprobacionPagoParteTest {

    private static final BigDecimal PARTE = new BigDecimal("353333.33");

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
        pago = Payment.builder().id(UUID.randomUUID()).reservationId(12L).userId(9L).parteNumero(2).amount(PARTE)
                .status(PaymentStatus.PENDING).currency("ARS").provider(PaymentProvider.MOCK)
                .createdAt(LocalDateTime.now()).build();
        lenient().when(paymentRepository.save(pago)).thenReturn(pago);
    }

    private void parteResponde(String estado, String motivo) {
        when(reservationClient.confirmarPagoParte(12L, 2, 9L, "MOCK-1", PARTE))
                .thenReturn(new ConfirmacionReservaResponse(estado, motivo, "mensaje de la reserva"));
    }

    @Test
    void unaParteQueNoEsLaUltimaQuedaAprobadaSinReembolso() {
        parteResponde("PARTE_PAGADA", null);

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(r.getTransactionId()).isEqualTo("MOCK-1");
        verify(paymentGatewayService, never()).refund(anyString(), any());
        verify(reservationClient, never()).confirmarPago(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void laUltimaParteQueConfirmaLaReservaTambienQuedaAprobada() {
        parteResponde("CONFIRMADA", null);

        assertThat(service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null).getStatus())
                .isEqualTo(PaymentStatus.APPROVED);
        verify(paymentGatewayService, never()).refund(anyString(), any());
    }

    @Test
    void elMontoQueSeInformaEsElDelProveedorConEscalaDos() {
        when(reservationClient.confirmarPagoParte(12L, 2, 9L, "MOCK-1", new BigDecimal("353333.00")))
                .thenReturn(new ConfirmacionReservaResponse("PARTE_PAGADA", null, "ok"));

        service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, new BigDecimal("353333"));

        verify(reservationClient).confirmarPagoParte(12L, 2, 9L, "MOCK-1", new BigDecimal("353333.00"));
    }

    @Test
    void unGrupoCanceladoOVencidoSeReembolsa() {
        parteResponde("CANCELADA", "PAGO_EN_GRUPO_VENCIDO");
        when(paymentGatewayService.refund("MOCK-1", PARTE))
                .thenReturn(RefundGatewayResponse.builder().approved(true).refundTransactionId("R-1").build());

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentHistoryService).saveHistory(eq(pago), eq(PaymentStatus.REFUNDED), contains("PAGO_EN_GRUPO_VENCIDO"));
    }

    @Test
    void unaParteQueYaNoEsDelPagadorSeReembolsa() {
        parteResponde("RECHAZADA", "PARTE_NO_ES_DEL_PAGADOR");
        when(paymentGatewayService.refund("MOCK-1", PARTE))
                .thenReturn(RefundGatewayResponse.builder().approved(true).build());

        assertThat(service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null).getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void siElReembolsoFallaQuedaAprobadoConReembolsoManualPendiente() {
        parteResponde("CANCELADA", "GRUPO_CANCELADO");
        when(paymentGatewayService.refund("MOCK-1", PARTE)).thenThrow(new RuntimeException("proveedor caído"));

        Payment r = service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentHistoryService).saveHistory(eq(pago), eq(PaymentStatus.APPROVED), contains("Reembolso manual pendiente"));
    }

    @Test
    void siReservationServiceNoRespondeSePropagaYNoSeReembolsa() {
        when(reservationClient.confirmarPagoParte(12L, 2, 9L, "MOCK-1", PARTE))
                .thenThrow(new ReservationClientException("sin respuesta"));

        assertThatThrownBy(() -> service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null))
                .isInstanceOf(ReservationClientException.class);
        verify(paymentGatewayService, never()).refund(anyString(), any());
    }

    @Test
    void unPagoYaAprobadoNoVuelveAConfirmarLaParte() {
        pago.setStatus(PaymentStatus.APPROVED);

        service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        verify(reservationClient, never()).confirmarPagoParte(anyLong(), anyInt(), anyLong(), anyString(), any());
    }

    @Test
    void unCobroDuplicadoDeMercadoPagoSobreUnaParteConfirmadaConEseCobroSeAdopta() {
        pago.setStatus(PaymentStatus.APPROVED);
        pago.setTransactionId("MP-1");
        pago.setProvider(PaymentProvider.MERCADO_PAGO);
        when(reservationClient.confirmarPagoParte(12L, 2, 9L, "MP-2", PARTE))
                .thenReturn(new ConfirmacionReservaResponse("PARTE_PAGADA", null, "ok"));
        when(paymentGatewayService.refund("MP-1", PARTE))
                .thenReturn(RefundGatewayResponse.builder().approved(true).build());

        service.reembolsarCobroDuplicado(pago, "MP-2", PARTE, PaymentMethod.CREDIT_CARD);

        assertThat(pago.getTransactionId()).isEqualTo("MP-2");
        assertThat(pago.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentGatewayService).refund("MP-1", PARTE);
    }

    @Test
    void unCobroDuplicadoQueLaReservaRechazaSeReembolsa() {
        pago.setStatus(PaymentStatus.APPROVED);
        pago.setTransactionId("MP-1");
        pago.setProvider(PaymentProvider.MERCADO_PAGO);
        when(reservationClient.confirmarPagoParte(12L, 2, 9L, "MP-2", PARTE))
                .thenReturn(new ConfirmacionReservaResponse("RECHAZADA", "PAGO_DUPLICADO", "ya pagada"));
        when(paymentGatewayService.refund("MP-2", PARTE))
                .thenReturn(RefundGatewayResponse.builder().approved(true).build());

        service.reembolsarCobroDuplicado(pago, "MP-2", PARTE, PaymentMethod.CREDIT_CARD);

        assertThat(pago.getTransactionId()).isEqualTo("MP-1");
        verify(paymentGatewayService).refund("MP-2", PARTE);
    }

    @Test
    void unPagoSinParteSigueUsandoPaymentConfirmed() {
        pago.setParteNumero(null);
        when(reservationClient.confirmarPago(12L, 9L, "MOCK-1", PARTE))
                .thenReturn(new ConfirmacionReservaResponse("CONFIRMADA", null, "Reserva confirmada."));

        service.aprobar(pago, "MOCK-1", PaymentMethod.CREDIT_CARD, null);

        verify(reservationClient).confirmarPago(12L, 9L, "MOCK-1", PARTE);
        verify(reservationClient, never()).confirmarPagoParte(anyLong(), anyInt(), anyLong(), anyString(), any());
    }

    @Test
    void unaParteCanceladaAlCerrarseElGrupoQueElProveedorApruebaDespuesSeReembolsa() {
        pago.setStatus(PaymentStatus.CANCELLED);
        pago.setProvider(PaymentProvider.MERCADO_PAGO);
        when(reservationClient.confirmarPagoParte(12L, 2, 9L, "MP-9", PARTE))
                .thenReturn(new ConfirmacionReservaResponse("CANCELADA", "GRUPO_CANCELADO", "El pago en grupo se cancelo."));
        when(paymentGatewayService.refund("MP-9", PARTE))
                .thenReturn(RefundGatewayResponse.builder().approved(true).refundTransactionId("R-9").build());

        Payment r = service.aprobar(pago, "MP-9", PaymentMethod.CREDIT_CARD, PARTE);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(r.getTransactionId()).isEqualTo("MP-9");
        verify(paymentGatewayService).refund("MP-9", PARTE);
        verify(paymentHistoryService).saveHistory(eq(pago), eq(PaymentStatus.REFUNDED), contains("GRUPO_CANCELADO"));
    }

    @Test
    void elPagoEnteroCanceladoAlPagarElOrganizadorSuParteQueSeApruebaDespuesSeReembolsa() {
        BigDecimal total = new BigDecimal("1060000.00");
        pago.setParteNumero(null);
        pago.setAmount(total);
        pago.setStatus(PaymentStatus.CANCELLED);
        pago.setProvider(PaymentProvider.MERCADO_PAGO);
        when(reservationClient.confirmarPago(12L, 9L, "MP-9", total))
                .thenReturn(new ConfirmacionReservaResponse("RECHAZADA", "PAGO_EN_GRUPO", "La reserva se paga en grupo."));
        when(paymentGatewayService.refund("MP-9", total))
                .thenReturn(RefundGatewayResponse.builder().approved(true).refundTransactionId("R-9").build());

        Payment r = service.aprobar(pago, "MP-9", PaymentMethod.CREDIT_CARD, total);

        assertThat(r.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentGatewayService).refund("MP-9", total);
        verify(paymentHistoryService).saveHistory(eq(pago), eq(PaymentStatus.REFUNDED), contains("PAGO_EN_GRUPO"));
        verify(reservationClient, never()).confirmarPagoParte(anyLong(), anyInt(), anyLong(), anyString(), any());
    }
}
