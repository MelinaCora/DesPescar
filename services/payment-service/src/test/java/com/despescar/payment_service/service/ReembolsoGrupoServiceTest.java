package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.despescar.payment_service.dto.response.ReembolsoGrupoResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.repository.PaymentRepository;

/** Reembolso de todas las partes pagadas de un grupo (D-b13, CB5). */
@ExtendWith(MockitoExtension.class)
class ReembolsoGrupoServiceTest {

    private static final BigDecimal PARTE = new BigDecimal("353333.33");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentHistoryService paymentHistoryService;
    @Mock
    private PaymentGatewayService paymentGatewayService;

    private ReembolsoGrupoService service;

    @BeforeEach
    void setUp() {
        service = new ReembolsoGrupoService(paymentRepository, paymentHistoryService, paymentGatewayService,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Payment pago(Integer parte, PaymentStatus estado, String transaccion) {
        Payment p = Payment.builder().id(UUID.randomUUID()).reservationId(12L).userId(9L).parteNumero(parte)
                .amount(PARTE).status(estado).transactionId(transaccion).currency("ARS")
                .provider(PaymentProvider.MOCK).createdAt(LocalDateTime.now()).build();
        lenient().when(paymentRepository.findByIdParaActualizar(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private void lista(Payment... pagos) {
        when(paymentRepository.idsDePartes(12L)).thenReturn(List.of(pagos).stream().map(Payment::getId).toList());
    }

    private void reembolsoAprobado(String transaccion) {
        when(paymentGatewayService.refund(transaccion, PARTE))
                .thenReturn(RefundGatewayResponse.builder().approved(true).refundTransactionId("R-" + transaccion).build());
    }

    @Test
    void reembolsaLosAprobadosCancelaLosPendientesYSalteaElResto() {
        Payment aprobado1 = pago(1, PaymentStatus.APPROVED, "MOCK-1");
        Payment aprobado2 = pago(2, PaymentStatus.APPROVED, "MOCK-2");
        Payment pendiente = pago(3, PaymentStatus.PENDING, null);
        Payment yaDevuelto = pago(4, PaymentStatus.REFUNDED, "MOCK-4");
        Payment rechazado = pago(5, PaymentStatus.REJECTED, null);
        lista(aprobado1, aprobado2, pendiente, yaDevuelto, rechazado);
        reembolsoAprobado("MOCK-1");
        reembolsoAprobado("MOCK-2");

        ReembolsoGrupoResponse r = service.reembolsarGrupo(12L, "PAGO_EN_GRUPO_VENCIDO");

        assertThat(r).isEqualTo(new ReembolsoGrupoResponse(2, 1, 0));
        assertThat(aprobado1.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(aprobado2.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(pendiente.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(yaDevuelto.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(rechazado.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        verify(paymentHistoryService).saveHistory(eq(aprobado1), eq(PaymentStatus.REFUNDED), contains("PAGO_EN_GRUPO_VENCIDO"));
        verify(paymentHistoryService).saveHistory(eq(pendiente), eq(PaymentStatus.CANCELLED), contains("PAGO_EN_GRUPO_VENCIDO"));
        verify(paymentGatewayService, never()).refund(eq("MOCK-4"), any());
    }

    @Test
    void unReembolsoQueElProveedorRechazaCuentaComoFallidoYQuedaAprobadoConAvisoManual() {
        Payment aprobado = pago(1, PaymentStatus.APPROVED, "MOCK-1");
        Payment otro = pago(2, PaymentStatus.APPROVED, "MOCK-2");
        lista(aprobado, otro);
        when(paymentGatewayService.refund("MOCK-1", PARTE))
                .thenReturn(RefundGatewayResponse.builder().approved(false).message("fondos no disponibles").build());
        reembolsoAprobado("MOCK-2");

        ReembolsoGrupoResponse r = service.reembolsarGrupo(12L, "GRUPO_CANCELADO");

        assertThat(r).isEqualTo(new ReembolsoGrupoResponse(1, 0, 1));
        assertThat(aprobado.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(otro.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentHistoryService).saveHistory(eq(aprobado), eq(PaymentStatus.APPROVED), contains("Reembolso manual pendiente"));
    }

    @Test
    void siElProveedorLanzaUnaExcepcionTambienEsFallidoYSigueConLosDemas() {
        Payment aprobado = pago(1, PaymentStatus.APPROVED, "MOCK-1");
        Payment pendiente = pago(2, PaymentStatus.PENDING, null);
        lista(aprobado, pendiente);
        when(paymentGatewayService.refund("MOCK-1", PARTE)).thenThrow(new RuntimeException("timeout"));

        ReembolsoGrupoResponse r = service.reembolsarGrupo(12L, "GRUPO_CANCELADO");

        assertThat(r).isEqualTo(new ReembolsoGrupoResponse(0, 1, 1));
        assertThat(pendiente.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void laSegundaLlamadaRespondeCeros() {
        Payment devuelto = pago(1, PaymentStatus.REFUNDED, "MOCK-1");
        Payment cancelado = pago(2, PaymentStatus.CANCELLED, null);
        lista(devuelto, cancelado);

        assertThat(service.reembolsarGrupo(12L, "GRUPO_CANCELADO")).isEqualTo(new ReembolsoGrupoResponse(0, 0, 0));
        verify(paymentGatewayService, never()).refund(anyString(), any());
    }

    @Test
    void unaReservaSinPagosDeParteRespondeCeros() {
        when(paymentRepository.idsDePartes(12L)).thenReturn(List.of());

        assertThat(service.reembolsarGrupo(12L, null)).isEqualTo(new ReembolsoGrupoResponse(0, 0, 0));
    }

    @Test
    void unPagoQueCambioEntreLaListaYElBloqueoSeReleeConSuEstadoActual() {
        Payment p = pago(1, PaymentStatus.APPROVED, "MOCK-1");
        lista(p);
        // Otro hilo lo reembolsó justo antes de que lo bloqueemos.
        p.setStatus(PaymentStatus.REFUNDED);

        assertThat(service.reembolsarGrupo(12L, "GRUPO_CANCELADO")).isEqualTo(new ReembolsoGrupoResponse(0, 0, 0));
        verify(paymentGatewayService, never()).refund(anyString(), any());
    }

    @Test
    void sinMotivoSeUsaGrupoCerrado() {
        Payment aprobado = pago(1, PaymentStatus.APPROVED, "MOCK-1");
        lista(aprobado);
        reembolsoAprobado("MOCK-1");

        service.reembolsarGrupo(12L, null);

        verify(paymentHistoryService).saveHistory(eq(aprobado), eq(PaymentStatus.REFUNDED), contains("GRUPO_CERRADO"));
    }
}
