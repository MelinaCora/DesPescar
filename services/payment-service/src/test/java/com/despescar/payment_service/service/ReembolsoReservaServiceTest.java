package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.despescar.payment_service.dto.response.ReembolsoReservaResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.entity.Refund;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.enums.RefundStatus;
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.repository.RefundRepository;

/** Reembolso de un monto de una reserva cancelada, repartido entre sus pagos aprobados. */
@ExtendWith(MockitoExtension.class)
class ReembolsoReservaServiceTest {

    private static final String MOTIVO = "CANCELADA_POR_USUARIO";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentHistoryService paymentHistoryService;
    @Mock
    private PaymentGatewayService paymentGatewayService;

    private ReembolsoReservaService service;
    private final List<Payment> pagos = new ArrayList<>();
    private final List<Refund> reembolsos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ReembolsoReservaService(paymentRepository, refundRepository, paymentHistoryService,
                paymentGatewayService, new TransactionTemplate(mock(PlatformTransactionManager.class)));
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentRepository.findByReservationId(15L)).thenReturn(pagos);
        lenient().when(refundRepository.findByPaymentReservationId(15L)).thenReturn(reembolsos);
        lenient().when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            reembolsos.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(paymentGatewayService.refund(any(), any())).thenAnswer(inv -> RefundGatewayResponse.builder()
                .approved(true).refundTransactionId("R-" + inv.getArgument(0)).build());
    }

    private Payment pago(String monto, PaymentStatus estado, String transaccion, int minuto) {
        Payment p = Payment.builder().id(UUID.randomUUID()).reservationId(15L).userId(9L)
                .amount(new BigDecimal(monto)).status(estado).transactionId(transaccion).currency("ARS")
                .provider(PaymentProvider.MOCK).createdAt(LocalDateTime.of(2026, 10, 1, 10, minuto)).build();
        lenient().when(paymentRepository.findByIdParaActualizar(p.getId())).thenReturn(Optional.of(p));
        pagos.add(p);
        return p;
    }

    private static List<BigDecimal> montos(String... valores) {
        return List.of(valores).stream().map(BigDecimal::new).toList();
    }

    @Test
    void reparteEnProporcionAEscalaDosYElRestoVaAlPrimero() {
        assertThat(ReembolsoReservaService.repartir(montos("100.00", "100.00", "100.00"), new BigDecimal("100.00")))
                .isEqualTo(montos("33.34", "33.33", "33.33"));
        assertThat(ReembolsoReservaService.repartir(montos("300.00", "100.00"), new BigDecimal("200.00")))
                .isEqualTo(montos("150.00", "50.00"));
    }

    @Test
    void nuncaReparteMasDeLoCobradoEnCadaPago() {
        assertThat(ReembolsoReservaService.repartir(montos("60.00", "40.00"), new BigDecimal("500.00")))
                .isEqualTo(montos("60.00", "40.00"));
        // El resto no entra en el primero (ya esta en su tope): pasa al siguiente
        assertThat(ReembolsoReservaService.repartir(montos("0.01", "0.03", "0.03"), new BigDecimal("0.06")))
                .isEqualTo(montos("0.01", "0.03", "0.02"));
    }

    @Test
    void unReembolsoParcialDejaElPagoAprobadoConSuHistorial() {
        Payment p = pago("1000.00", PaymentStatus.APPROVED, "MOCK-1", 0);

        ReembolsoReservaResponse r = service.reembolsar(15L, new BigDecimal("400.00"), MOTIVO);

        assertThat(r).isEqualTo(new ReembolsoReservaResponse(new BigDecimal("400.00"), 0));
        verify(paymentGatewayService).refund("MOCK-1", new BigDecimal("400.00"));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentHistoryService).saveHistory(eq(p), eq(PaymentStatus.APPROVED), contains("Reembolso parcial $400.00"));
        assertThat(reembolsos).singleElement().satisfies(x -> {
            assertThat(x.getAmount()).isEqualByComparingTo("400.00");
            assertThat(x.getStatus()).isEqualTo(RefundStatus.APPROVED);
            assertThat(x.getReason()).isEqualTo(MOTIVO);
        });
    }

    @Test
    void elReembolsoCompletoDejaElPagoReembolsado() {
        Payment p = pago("1000.00", PaymentStatus.APPROVED, "MOCK-1", 0);

        service.reembolsar(15L, new BigDecimal("1000.00"), MOTIVO);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentHistoryService).saveHistory(eq(p), eq(PaymentStatus.REFUNDED), contains("Reembolso"));
    }

    @Test
    void lasPartesDeUnGrupoSeReembolsanEnProporcionYLosNoAprobadosNoCuentan() {
        pago("600.00", PaymentStatus.APPROVED, "MOCK-1", 0);
        pago("400.00", PaymentStatus.APPROVED, "MOCK-2", 1);
        pago("400.00", PaymentStatus.REJECTED, null, 2);

        ReembolsoReservaResponse r = service.reembolsar(15L, new BigDecimal("500.00"), MOTIVO);

        assertThat(r).isEqualTo(new ReembolsoReservaResponse(new BigDecimal("500.00"), 0));
        verify(paymentGatewayService).refund("MOCK-1", new BigDecimal("300.00"));
        verify(paymentGatewayService).refund("MOCK-2", new BigDecimal("200.00"));
    }

    @Test
    void repetirElPedidoNoReembolsaDosVeces() {
        pago("600.00", PaymentStatus.APPROVED, "MOCK-1", 0);
        pago("400.00", PaymentStatus.APPROVED, "MOCK-2", 1);
        service.reembolsar(15L, new BigDecimal("1000.00"), MOTIVO);

        ReembolsoReservaResponse otraVez = service.reembolsar(15L, new BigDecimal("1000.00"), MOTIVO);

        assertThat(otraVez).isEqualTo(new ReembolsoReservaResponse(new BigDecimal("1000.00"), 0));
        verify(paymentGatewayService).refund("MOCK-1", new BigDecimal("600.00"));
        verify(paymentGatewayService).refund("MOCK-2", new BigDecimal("400.00"));
    }

    @Test
    void siElProveedorRechazaUnoQuedaComoManualYElReintentoSoloPideEse() {
        Payment uno = pago("600.00", PaymentStatus.APPROVED, "MOCK-1", 0);
        Payment dos = pago("400.00", PaymentStatus.APPROVED, "MOCK-2", 1);
        when(paymentGatewayService.refund("MOCK-2", new BigDecimal("200.00")))
                .thenReturn(RefundGatewayResponse.builder().approved(false).message("sin saldo").build())
                .thenReturn(RefundGatewayResponse.builder().approved(true).refundTransactionId("R-2").build());

        ReembolsoReservaResponse r = service.reembolsar(15L, new BigDecimal("500.00"), MOTIVO);

        assertThat(r).isEqualTo(new ReembolsoReservaResponse(new BigDecimal("300.00"), 1));
        assertThat(dos.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentHistoryService).saveHistory(eq(dos), eq(PaymentStatus.APPROVED), contains("Reembolso manual pendiente"));

        ReembolsoReservaResponse reintento = service.reembolsar(15L, new BigDecimal("500.00"), MOTIVO);

        assertThat(reintento).isEqualTo(new ReembolsoReservaResponse(new BigDecimal("500.00"), 0));
        verify(paymentGatewayService).refund("MOCK-1", new BigDecimal("300.00"));
        assertThat(uno.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void sinPagosAprobadosNoLlamaAlProveedor() {
        pago("400.00", PaymentStatus.PENDING, null, 0);

        ReembolsoReservaResponse r = service.reembolsar(15L, new BigDecimal("100.00"), MOTIVO);

        assertThat(r).isEqualTo(new ReembolsoReservaResponse(new BigDecimal("0.00"), 0));
        verify(paymentGatewayService, never()).refund(any(), any());
    }
}
