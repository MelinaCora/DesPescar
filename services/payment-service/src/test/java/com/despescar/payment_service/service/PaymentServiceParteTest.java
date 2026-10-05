package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ParteReservaResponse;
import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.ReservationAmountResolutionException;
import com.despescar.payment_service.exception.ReservationClientException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;

/** Cobro de una parte (D-b9, CB4). El mapper es el real: el test mira la entidad que se guarda. */
@ExtendWith(MockitoExtension.class)
class PaymentServiceParteTest {

    private static final BigDecimal PARTE = new BigDecimal("353333.33");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentHistoryService paymentHistoryService;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private ReservationClient reservationClient;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new PaymentService(paymentRepository, new PaymentMapper(), paymentHistoryService,
                paymentGatewayService, reservationClient, tx);
        lenient().when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MOCK);
        lenient().when(paymentRepository.findByReservationId(12L)).thenReturn(List.of());
        // El PENDING que se inserta queda guardado acá y es lo que findById devuelve después del checkout.
        AtomicReference<Payment> guardado = new AtomicReference<>();
        lenient().when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
                guardado.set(p);
            }
            return p;
        });
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(guardado.get()));
        lenient().when(paymentGatewayService.createCheckout(anyString(), any(), anyString()))
                .thenAnswer(inv -> PaymentCheckoutResponse.builder().preferenceId("MOCK-PREF")
                        .checkoutUrl("/pago/simulado?pago=" + inv.getArgument(0)).build());
    }

    private static PaymentRequest pedido(Integer parte) {
        return PaymentRequest.builder().reservationId(12L).parteNumero(parte).build();
    }

    private static ParteReservaResponse parte(Long usuario, String estadoParte, String estadoGrupo, long segundos,
                                              BigDecimal monto, String moneda) {
        ParteReservaResponse p = new ParteReservaResponse();
        p.setReservaId(12L);
        p.setNumero(2);
        p.setUsuarioId(usuario);
        p.setMonto(monto);
        p.setMoneda(moneda);
        p.setEstadoParte(estadoParte);
        p.setEstadoGrupo(estadoGrupo);
        p.setSegundosRestantes(segundos);
        return p;
    }

    private void parteResponde(ParteReservaResponse p) {
        when(reservationClient.getParte(12L, 2)).thenReturn(Optional.of(p));
    }

    @Test
    void cobraElMontoDeLaParteYGuardaSuNumero() {
        parteResponde(parte(9L, "TOMADA", "ABIERTO", 86100, PARTE, "ARS"));

        PaymentResponse r = service.createPayment(pedido(2), 9L);

        assertThat(r.getParteNumero()).isEqualTo(2);
        assertThat(r.getAmount()).isEqualByComparingTo(PARTE);
        assertThat(r.getReservationId()).isEqualTo(12L);
        assertThat(r.getCheckoutUrl()).startsWith("/pago/simulado?pago=");
        verify(reservationClient, never()).getReservation(any());
        verify(paymentHistoryService).saveHistory(any(), eq(PaymentStatus.PENDING), eq("Payment created and is pending."));
    }

    @Test
    void elMontoDeLaParteSeRedondeaAEscalaDos() {
        parteResponde(parte(9L, "TOMADA", "ABIERTO", 86100, new BigDecimal("353333.3"), "ARS"));

        assertThat(service.createPayment(pedido(2), 9L).getAmount()).isEqualTo(new BigDecimal("353333.30"));
    }

    @Test
    void soloElDuenoDeLaPartePuedePagarla() {
        parteResponde(parte(9L, "TOMADA", "ABIERTO", 86100, PARTE, "ARS"));

        assertThatThrownBy(() -> service.createPayment(pedido(2), 7L)).isInstanceOf(AccessDeniedException.class);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaParteLibreEsUn409() {
        parteResponde(parte(null, "LIBRE", "ABIERTO", 86100, PARTE, "ARS"));

        assertThatThrownBy(() -> service.createPayment(pedido(2), 9L))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("sumarte");
    }

    @Test
    void unaPartePagadaEsUn409() {
        parteResponde(parte(9L, "PAGADA", "ABIERTO", 86100, PARTE, "ARS"));

        assertThatThrownBy(() -> service.createPayment(pedido(2), 9L))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("ya está pagada");
    }

    @Test
    void unGrupoQueNoEstaAbiertoOVencidoEsUn409() {
        when(reservationClient.getParte(12L, 2))
                .thenReturn(Optional.of(parte(9L, "TOMADA", "CANCELADO", 0, PARTE, "ARS")))
                .thenReturn(Optional.of(parte(9L, "TOMADA", "COMPLETO", 100, PARTE, "ARS")))
                .thenReturn(Optional.of(parte(9L, "TOMADA", "ABIERTO", 0, PARTE, "ARS")));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.createPayment(pedido(2), 9L)).isInstanceOf(InvalidPaymentStateException.class);
        }
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaParteQueNoExisteEsUn409() {
        when(reservationClient.getParte(12L, 2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPayment(pedido(2), 9L)).isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void monedaDistintaDeArsOMontoCeroEsUn422() {
        when(reservationClient.getParte(12L, 2))
                .thenReturn(Optional.of(parte(9L, "TOMADA", "ABIERTO", 86100, PARTE, "USD")))
                .thenReturn(Optional.of(parte(9L, "TOMADA", "ABIERTO", 86100, BigDecimal.ZERO, "ARS")));

        assertThatThrownBy(() -> service.createPayment(pedido(2), 9L)).isInstanceOf(ReservationAmountResolutionException.class);
        assertThatThrownBy(() -> service.createPayment(pedido(2), 9L)).isInstanceOf(ReservationAmountResolutionException.class);
    }

    @Test
    void siReservationServiceNoRespondeSePropagaElErrorDeComunicacion() {
        when(reservationClient.getParte(12L, 2)).thenThrow(new ReservationClientException("caído"));

        assertThatThrownBy(() -> service.createPayment(pedido(2), 9L)).isInstanceOf(ReservationClientException.class);
    }

    @Test
    void repetirElPedidoDevuelveElMismoPendingDeEsaParte() {
        parteResponde(parte(9L, "TOMADA", "ABIERTO", 86100, PARTE, "ARS"));
        Payment previo = Payment.builder().id(UUID.randomUUID()).reservationId(12L).userId(9L).parteNumero(2)
                .amount(PARTE).currency("ARS").status(PaymentStatus.PENDING).provider(PaymentProvider.MOCK)
                .checkoutUrl("/pago/simulado?pago=x").createdAt(LocalDateTime.now()).build();
        when(paymentRepository.findByReservationId(12L)).thenReturn(List.of(previo));

        PaymentResponse r = service.createPayment(pedido(2), 9L);

        assertThat(r.getId()).isEqualTo(previo.getId());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(paymentGatewayService, never()).createCheckout(anyString(), any(), anyString());
    }

    @Test
    void siCambioElMontoDeLaParteElPendingAnteriorSeReemplazaYElHistorialLoDice() {
        parteResponde(parte(9L, "TOMADA", "ABIERTO", 86100, PARTE, "ARS"));
        Payment previo = Payment.builder().id(UUID.randomUUID()).reservationId(12L).userId(9L).parteNumero(2)
                .amount(new BigDecimal("530000.00")).currency("ARS").status(PaymentStatus.PENDING)
                .provider(PaymentProvider.MOCK).checkoutUrl("/pago/simulado?pago=x").createdAt(LocalDateTime.now()).build();
        when(paymentRepository.findByReservationId(12L)).thenReturn(List.of(previo));

        PaymentResponse r = service.createPayment(pedido(2), 9L);

        assertThat(r.getId()).isNotEqualTo(previo.getId());
        assertThat(r.getAmount()).isEqualByComparingTo(PARTE);
        assertThat(previo.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentHistoryService).saveHistory(previo, PaymentStatus.CANCELLED,
                "Reemplazado por un pago nuevo: cambio el monto de la parte.");
    }

    @Test
    void elPendingDeOtraParteDelMismoUsuarioNoSeReutilizaNiSeCancela() {
        parteResponde(parte(9L, "TOMADA", "ABIERTO", 86100, PARTE, "ARS"));
        Payment otraParte = Payment.builder().id(UUID.randomUUID()).reservationId(12L).userId(9L).parteNumero(3)
                .amount(PARTE).currency("ARS").status(PaymentStatus.PENDING).provider(PaymentProvider.MOCK)
                .checkoutUrl("/pago/simulado?pago=y").createdAt(LocalDateTime.now()).build();
        when(paymentRepository.findByReservationId(12L)).thenReturn(List.of(otraParte));

        PaymentResponse r = service.createPayment(pedido(2), 9L);

        assertThat(r.getId()).isNotEqualTo(otraParte.getId());
        assertThat(otraParte.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void alPagarSuParteElOrganizadorCancelaElPendingSinParteQueHabiaAbiertoAntesDeDividir() {
        when(reservationClient.getParte(12L, 1)).thenReturn(Optional.of(parte(7L, "TOMADA", "ABIERTO", 86100,
                new BigDecimal("353333.34"), "ARS")));
        Payment entero = Payment.builder().id(UUID.randomUUID()).reservationId(12L).userId(7L).parteNumero(null)
                .amount(new BigDecimal("1060000.00")).currency("ARS").status(PaymentStatus.PENDING)
                .provider(PaymentProvider.MOCK).checkoutUrl("/pago/simulado?pago=z").createdAt(LocalDateTime.now()).build();
        when(paymentRepository.findByReservationId(12L)).thenReturn(List.of(entero));

        service.createPayment(pedido(1), 7L);

        assertThat(entero.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentHistoryService).saveHistory(entero, PaymentStatus.CANCELLED,
                "Reemplazado: la reserva ahora se paga en grupo.");
    }

    @Test
    void elPagoDeOtroUsuarioParaLaMismaReservaNoSeToca() {
        parteResponde(parte(9L, "TOMADA", "ABIERTO", 86100, PARTE, "ARS"));
        Payment deOtro = Payment.builder().id(UUID.randomUUID()).reservationId(12L).userId(7L).parteNumero(1)
                .amount(PARTE).currency("ARS").status(PaymentStatus.PENDING).provider(PaymentProvider.MOCK)
                .createdAt(LocalDateTime.now()).build();
        when(paymentRepository.findByReservationId(12L)).thenReturn(List.of(deOtro));

        service.createPayment(pedido(2), 9L);

        assertThat(deOtro.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void sinParteNumeroElCaminoDeUnSoloPagadorNoCambia() {
        com.despescar.payment_service.client.dto.ReservationResponse carrito =
                new com.despescar.payment_service.client.dto.ReservationResponse();
        carrito.setIdCarrito(12L);
        carrito.setCreadorId(7L);
        carrito.setEstadoGeneral("PENDIENTE_PAGO");
        carrito.setSegundosRestantes(600L);
        carrito.setMontoTotal(new BigDecimal("1060000.00"));
        carrito.setMoneda("ARS");
        when(reservationClient.getReservation(12L)).thenReturn(carrito);

        PaymentResponse r = service.createPayment(pedido(null), 7L);

        assertThat(r.getParteNumero()).isNull();
        assertThat(r.getAmount()).isEqualByComparingTo("1060000.00");
        verify(reservationClient, never()).getParte(any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
