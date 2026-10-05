package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ReservationResponse;
import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.exception.ReservationAmountResolutionException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final BigDecimal TOTAL = new BigDecimal("1060000.00");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentHistoryService paymentHistoryService;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @Mock
    private ReservationClient reservationClient;

    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void cobraElMontoTotalDelCarritoEnPesosYDevuelveElCheckout() {
        PaymentRequest request = paymentRequest();
        Payment mapped = payment(77L, 55L, PaymentStatus.PENDING, TOTAL);
        when(reservationClient.getReservation(77L)).thenReturn(carrito(55L, "PENDIENTE_PAGO", 600L, TOTAL, "ARS"));
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MOCK);
        when(paymentRepository.findByReservationId(77L)).thenReturn(List.of());
        when(paymentMapper.toEntity(request, TOTAL, "ARS", 55L)).thenReturn(mapped);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(paymentGatewayService.createCheckout(anyString(), eq(TOTAL), eq("ARS")))
                .thenAnswer(inv -> PaymentCheckoutResponse.builder()
                        .preferenceId("MOCK-PREF-" + inv.getArgument(0))
                        .checkoutUrl("/pago/simulado?pago=" + inv.getArgument(0))
                        .build());
        when(paymentRepository.findById(any(UUID.class))).thenAnswer(inv -> Optional.of(mapped));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(PaymentResponse.builder().build());

        paymentService.createPayment(request, 55L);

        assertThat(mapped.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(mapped.getProvider()).isEqualTo(PaymentProvider.MOCK);
        assertThat(mapped.getCheckoutUrl()).isEqualTo("/pago/simulado?pago=" + mapped.getId());
        verify(paymentRepository).saveAndFlush(any(Payment.class));
        verify(paymentRepository).save(any(Payment.class));
        verify(paymentHistoryService).saveHistory(mapped, PaymentStatus.PENDING, "Payment created and is pending.");
    }

    @Test
    void soloPagaElCreadorDelCarrito() {
        when(reservationClient.getReservation(77L)).thenReturn(carrito(99L, "PENDIENTE_PAGO", 600L, TOTAL, "ARS"));

        assertThatThrownBy(() -> paymentService.createPayment(paymentRequest(), 55L))
                .isInstanceOf(AccessDeniedException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void soloSePagaUnCarritoEnPendientePagoSinVencer() {
        when(reservationClient.getReservation(77L))
                .thenReturn(carrito(55L, "INICIADA", 600L, TOTAL, "ARS"))
                .thenReturn(carrito(55L, "PENDIENTE_PAGO", 0L, TOTAL, "ARS"))
                .thenReturn(carrito(55L, "CONFIRMADA", 0L, TOTAL, "ARS"));

        assertThatThrownBy(() -> paymentService.createPayment(paymentRequest(), 55L))
                .isInstanceOf(InvalidPaymentStateException.class).hasMessageContaining("INICIADA");
        assertThatThrownBy(() -> paymentService.createPayment(paymentRequest(), 55L))
                .isInstanceOf(InvalidPaymentStateException.class).hasMessageContaining("vencio");
        assertThatThrownBy(() -> paymentService.createPayment(paymentRequest(), 55L))
                .isInstanceOf(InvalidPaymentStateException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void sinMonedaEnPesosOSinMontoResponde422() {
        when(reservationClient.getReservation(77L))
                .thenReturn(carrito(55L, "PENDIENTE_PAGO", 600L, TOTAL, null))
                .thenReturn(carrito(55L, "PENDIENTE_PAGO", 600L, TOTAL, "USD"))
                .thenReturn(carrito(55L, "PENDIENTE_PAGO", 600L, BigDecimal.ZERO, "ARS"));

        assertThatThrownBy(() -> paymentService.createPayment(paymentRequest(), 55L))
                .isInstanceOf(ReservationAmountResolutionException.class)
                .hasMessage("La reserva no define una moneda unica para calcular el pago.");
        assertThatThrownBy(() -> paymentService.createPayment(paymentRequest(), 55L))
                .isInstanceOf(ReservationAmountResolutionException.class).hasMessageContaining("ARS");
        assertThatThrownBy(() -> paymentService.createPayment(paymentRequest(), 55L))
                .isInstanceOf(ReservationAmountResolutionException.class);
    }

    @Test
    void unDobleClicDevuelveElMismoPagoPendiente() {
        Payment previo = payment(77L, 55L, PaymentStatus.PENDING, TOTAL);
        previo.setId(UUID.randomUUID());
        previo.setProvider(PaymentProvider.MOCK);
        previo.setCheckoutUrl("/pago/simulado?pago=" + previo.getId());
        PaymentResponse respuesta = PaymentResponse.builder().id(previo.getId()).build();
        when(reservationClient.getReservation(77L)).thenReturn(carrito(55L, "PENDIENTE_PAGO", 600L, TOTAL, "ARS"));
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MOCK);
        when(paymentRepository.findByReservationId(77L)).thenReturn(List.of(previo));
        when(paymentMapper.toResponse(previo)).thenReturn(respuesta);

        PaymentResponse r = paymentService.createPayment(paymentRequest(), 55L);

        assertThat(r.getId()).isEqualTo(previo.getId());
        verify(paymentGatewayService, never()).createCheckout(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void siCambioElTotalCancelaElPendienteAnteriorYCreaOtro() {
        PaymentRequest request = paymentRequest();
        Payment viejo = payment(77L, 55L, PaymentStatus.PENDING, new BigDecimal("480000.00"));
        viejo.setId(UUID.randomUUID());
        viejo.setProvider(PaymentProvider.MOCK);
        viejo.setCheckoutUrl("/pago/simulado?pago=" + viejo.getId());
        Payment nuevo = payment(77L, 55L, PaymentStatus.PENDING, TOTAL);
        when(reservationClient.getReservation(77L)).thenReturn(carrito(55L, "PENDIENTE_PAGO", 600L, TOTAL, "ARS"));
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MOCK);
        when(paymentRepository.findByReservationId(77L)).thenReturn(List.of(viejo));
        when(paymentMapper.toEntity(request, TOTAL, "ARS", 55L)).thenReturn(nuevo);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(paymentGatewayService.createCheckout(anyString(), eq(TOTAL), eq("ARS")))
                .thenReturn(PaymentCheckoutResponse.builder().preferenceId("p").checkoutUrl("/pago/simulado?pago=x").build());
        when(paymentRepository.findById(any(UUID.class))).thenReturn(Optional.of(nuevo));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(PaymentResponse.builder().build());

        paymentService.createPayment(request, 55L);

        InOrder orden = inOrder(paymentRepository);
        orden.verify(paymentRepository).saveAndFlush(viejo);
        orden.verify(paymentRepository).saveAndFlush(nuevo);
        assertThat(viejo.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentHistoryService).saveHistory(viejo, PaymentStatus.CANCELLED,
                "Reemplazado por un pago nuevo: cambio el total del carrito.");
        assertThat(nuevo.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void siOtroRequestGanoLaCarreraDevuelveSuPagoPendiente() {
        PaymentRequest request = paymentRequest();
        Payment ganador = payment(77L, 55L, PaymentStatus.PENDING, TOTAL);
        ganador.setId(UUID.randomUUID());
        ganador.setProvider(PaymentProvider.MOCK);
        ganador.setCheckoutUrl("/pago/simulado?pago=" + ganador.getId());
        when(reservationClient.getReservation(77L)).thenReturn(carrito(55L, "PENDIENTE_PAGO", 600L, TOTAL, "ARS"));
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MOCK);
        // primer intento: no se ve nada y el insert choca con el indice; segundo: ya esta el ganador
        when(paymentRepository.findByReservationId(77L)).thenReturn(List.of()).thenReturn(List.of(ganador));
        when(paymentMapper.toEntity(request, TOTAL, "ARS", 55L))
                .thenReturn(payment(77L, 55L, PaymentStatus.PENDING, TOTAL));
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("uk"));
        when(paymentMapper.toResponse(ganador)).thenReturn(PaymentResponse.builder().id(ganador.getId()).build());

        PaymentResponse r = paymentService.createPayment(request, 55L);

        assertThat(r.getId()).isEqualTo(ganador.getId());
        verify(paymentGatewayService, never()).createCheckout(any(), any(), any());
    }

    @Test
    void elMontoSeNormalizaAEscala2() {
        for (String[] caso : new String[][] {{"1060000", "1060000.00"}, {"1060000.005", "1060000.01"}}) {
            org.mockito.Mockito.reset(paymentRepository, paymentMapper, paymentGatewayService, reservationClient);
            BigDecimal esperado = new BigDecimal(caso[1]);
            PaymentRequest request = paymentRequest();
            Payment mapped = payment(77L, 55L, PaymentStatus.PENDING, esperado);
            when(reservationClient.getReservation(77L))
                    .thenReturn(carrito(55L, "PENDIENTE_PAGO", 600L, new BigDecimal(caso[0]), "ARS"));
            when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MOCK);
            when(paymentRepository.findByReservationId(77L)).thenReturn(List.of());
            when(paymentMapper.toEntity(request, esperado, "ARS", 55L)).thenReturn(mapped);
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(paymentRepository.findById(any(UUID.class))).thenReturn(Optional.of(mapped));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentGatewayService.createCheckout(anyString(), eq(esperado), eq("ARS")))
                    .thenReturn(PaymentCheckoutResponse.builder().preferenceId("p").checkoutUrl("/u").build());
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(PaymentResponse.builder().build());

            paymentService.createPayment(request, 55L);

            verify(paymentMapper).toEntity(request, esperado, "ARS", 55L);
            verify(paymentGatewayService).createCheckout(anyString(), eq(esperado), eq("ARS"));
        }
    }

    @Test
    void getPaymentByIdShouldThrowWhenPaymentDoesNotExist() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(paymentId, 11L))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found with id: " + paymentId);
    }

    @Test
    void getPaymentsByReservationShouldReturnAllMatches() {
        Payment first = payment(99L, 11L, PaymentStatus.PENDING, TOTAL);
        first.setId(UUID.randomUUID());
        Payment second = payment(99L, 11L, PaymentStatus.PENDING, TOTAL);
        second.setId(UUID.randomUUID());

        when(paymentRepository.findByReservationId(99L)).thenReturn(List.of(first, second));
        when(paymentMapper.toResponse(first)).thenReturn(PaymentResponse.builder().id(first.getId()).build());
        when(paymentMapper.toResponse(second)).thenReturn(PaymentResponse.builder().id(second.getId()).build());

        List<PaymentResponse> responses = paymentService.getPaymentsByReservation(99L, 11L);

        assertThat(responses).extracting(PaymentResponse::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void cancelPaymentShouldRejectNonPendingPayments() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = payment(88L, 11L, PaymentStatus.APPROVED, TOTAL);
        payment.setId(paymentId);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment(paymentId, 11L))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessage("Payment cannot be cancelled because its current status is: APPROVED");
    }

    @Test
    void cancelPaymentShouldUpdateStatusAndWriteHistory() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = payment(88L, 11L, PaymentStatus.PENDING, TOTAL);
        payment.setId(paymentId);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(PaymentResponse.builder()
                .id(paymentId)
                .status(PaymentStatus.CANCELLED)
                .build());

        PaymentResponse response = paymentService.cancelPayment(paymentId, 11L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentHistoryService).saveHistory(payment, PaymentStatus.CANCELLED, "Payment cancelled.");
    }

    private PaymentRequest paymentRequest() {
        return PaymentRequest.builder().reservationId(77L).build();
    }

    private Payment payment(Long reservationId, Long userId, PaymentStatus status, BigDecimal amount) {
        return Payment.builder()
                .reservationId(reservationId)
                .userId(userId)
                .amount(amount)
                .status(status)
                .currency("ARS")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ReservationResponse carrito(Long creadorId, String estado, Long segundos, BigDecimal monto, String moneda) {
        ReservationResponse r = new ReservationResponse();
        r.setIdCarrito(77L);
        r.setCreadorId(creadorId);
        r.setEstadoGeneral(estado);
        r.setSegundosRestantes(segundos);
        r.setMontoTotal(monto);
        r.setMoneda(moneda);
        return r;
    }
}
