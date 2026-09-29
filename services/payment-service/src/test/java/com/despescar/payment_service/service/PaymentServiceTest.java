package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createPaymentShouldPersistPendingPaymentAndReturnCheckoutUrl() {
        PaymentRequest request = paymentRequest();
        ReservationResponse reservation = reservationResponse(request.getUserId(), "ARS");
        Payment mappedPayment = payment(request.getReservationId(), request.getUserId());
        PaymentResponse mappedResponse = PaymentResponse.builder()
                .id(UUID.randomUUID())
                .reservationId(request.getReservationId())
                .userId(request.getUserId())
                .amount(new BigDecimal("15000.00"))
                .status(PaymentStatus.PENDING)
                .preferenceId("pref-123")
                .checkoutUrl("https://checkout.test/payments/123")
                .currency("ARS")
                .createdAt(LocalDateTime.now())
                .build();

        when(reservationClient.getReservation(request.getReservationId())).thenReturn(reservation);
        when(paymentMapper.toEntity(request, new BigDecimal("15000.00"), "ARS")).thenReturn(mappedPayment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(UUID.randomUUID());
            }
            return payment;
        });
        when(paymentGatewayService.createCheckout(any(), eq(new BigDecimal("15000.00")), eq("ARS")))
                .thenReturn(PaymentCheckoutResponse.builder()
                        .preferenceId("pref-123")
                        .checkoutUrl("https://checkout.test/payments/123")
                        .message("ok")
                        .build());
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(mappedResponse);

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.test/payments/123");
        assertThat(mappedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(mappedPayment.getPreferenceId()).isEqualTo("pref-123");
        assertThat(mappedPayment.getCheckoutUrl()).isEqualTo("https://checkout.test/payments/123");
        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(paymentHistoryService).saveHistory(mappedPayment, PaymentStatus.PENDING, "Payment created and is pending.");
    }

    @Test
    void createPaymentShouldRejectReservationWithoutCurrency() {
        PaymentRequest request = paymentRequest();
        ReservationResponse reservation = reservationResponse(request.getUserId(), null);

        when(reservationClient.getReservation(request.getReservationId())).thenReturn(reservation);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(ReservationAmountResolutionException.class)
                .hasMessage("La reserva no define una moneda unica para calcular el pago.");
    }

    @Test
    void getPaymentByIdShouldThrowWhenPaymentDoesNotExist() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(paymentId))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found with id: " + paymentId);
    }

    @Test
    void getPaymentsByReservationShouldReturnAllMatches() {
        Long reservationId = 99L;
        Payment firstPayment = payment(reservationId, 11L);
        firstPayment.setId(UUID.randomUUID());
        Payment secondPayment = payment(reservationId, 11L);
        secondPayment.setId(UUID.randomUUID());

        when(paymentRepository.findByReservationId(reservationId)).thenReturn(List.of(firstPayment, secondPayment));
        when(paymentMapper.toResponse(firstPayment)).thenReturn(PaymentResponse.builder().id(firstPayment.getId()).build());
        when(paymentMapper.toResponse(secondPayment)).thenReturn(PaymentResponse.builder().id(secondPayment.getId()).build());

        List<PaymentResponse> responses = paymentService.getPaymentsByReservation(reservationId);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(PaymentResponse::getId)
                .containsExactly(firstPayment.getId(), secondPayment.getId());
    }

    @Test
    void cancelPaymentShouldRejectNonPendingPayments() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = payment(88L, 11L);
        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.APPROVED);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment(paymentId))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessage("Payment cannot be cancelled because its current status is: APPROVED");
    }

    @Test
    void cancelPaymentShouldUpdateStatusAndWriteHistory() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = payment(88L, 11L);
        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(PaymentResponse.builder()
                .id(paymentId)
                .status(PaymentStatus.CANCELLED)
                .build());

        PaymentResponse response = paymentService.cancelPayment(paymentId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentHistoryService).saveHistory(payment, PaymentStatus.CANCELLED, "Payment cancelled.");
    }

    private PaymentRequest paymentRequest() {
        return PaymentRequest.builder()
                .reservationId(77L)
                .userId(55L)
                .build();
    }

    private Payment payment(Long reservationId, Long userId) {
        return Payment.builder()
                .reservationId(reservationId)
                .userId(userId)
                .amount(new BigDecimal("15000.00"))
                .status(PaymentStatus.PENDING)
                .currency("ARS")
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ReservationResponse reservationResponse(Long userId, String currency) {
        ReservationResponse response = new ReservationResponse();
        response.setIdCarrito(77L);
        response.setMoneda(currency);
        response.setMontoTotal(new BigDecimal("15000.00"));

        ReservationResponse.SeatDetail firstSeat = new ReservationResponse.SeatDetail();
        firstSeat.setPagadorId(userId);
        firstSeat.setPrecioCobrado(new BigDecimal("10000.00"));
        firstSeat.setEstadoPago("PENDIENTE");

        ReservationResponse.SeatDetail secondSeat = new ReservationResponse.SeatDetail();
        secondSeat.setPagadorId(userId);
        secondSeat.setPrecioCobrado(new BigDecimal("5000.00"));
        secondSeat.setEstadoPago("PENDIENTE");

        response.setAsientos(List.of(firstSeat, secondSeat));
        return response;
    }
}
