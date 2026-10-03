package com.despescar.payment_service.service;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ReservationResponse;
import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.exception.ReservationAmountResolutionException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentHistoryService paymentHistoryService;
    private final PaymentGatewayService paymentGatewayService;
    private final ReservationClient reservationClient;

    @Transactional
        public PaymentResponse createPayment(PaymentRequest request, Long authenticatedUserId) {
        ReservationResponse reservation = reservationClient.getReservation(request.getReservationId());
                PaymentAmountResolution amountResolution = resolveAmountForPayer(
                                reservation,
                                request.getReservationId(),
                                authenticatedUserId
                );

        Payment payment = paymentMapper.toEntity(
                request,
                amountResolution.amount(),
                amountResolution.currency(),
                authenticatedUserId
        );

        payment.setStatus(PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.save(payment);

        paymentHistoryService.saveHistory(
                savedPayment,
                PaymentStatus.PENDING,
                "Payment created and is pending."
        );

        PaymentCheckoutResponse checkout =
                paymentGatewayService.createCheckout(
                        savedPayment.getId().toString(),
                        savedPayment.getAmount(),
                        savedPayment.getCurrency()
                );

        savedPayment.setPreferenceId(
                checkout.getPreferenceId()
        );
        savedPayment.setCheckoutUrl(
                checkout.getCheckoutUrl()
        );

        Payment updatedPayment =
                paymentRepository.save(savedPayment);

        return paymentMapper.toResponse(updatedPayment);
    }

    @Transactional(readOnly = true)
        public PaymentResponse getPaymentById(UUID paymentId, Long authenticatedUserId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + paymentId
                        ));

        requireOwner(payment, authenticatedUserId);
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
        public List<PaymentResponse> getPaymentsByUser(Long userId, Long authenticatedUserId) {
                if (!authenticatedUserId.equals(userId)) {
                        throw new AccessDeniedException("No puedes consultar pagos de otro usuario.");
                }

        return paymentRepository.findByUserId(userId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByReservation(
            Long reservationId,
            Long authenticatedUserId) {

        return paymentRepository.findByReservationId(reservationId)
                .stream()
                .filter(payment -> authenticatedUserId.equals(payment.getUserId()))
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional
        public PaymentResponse cancelPayment(UUID paymentId, Long authenticatedUserId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + paymentId
                        ));

        requireOwner(payment, authenticatedUserId);
        if (payment.getStatus() != PaymentStatus.PENDING) {

            throw new InvalidPaymentStateException(
                    "Payment cannot be cancelled because its current status is: "
                            + payment.getStatus()
            );
        }

        payment.setStatus(PaymentStatus.CANCELLED);

        Payment updatedPayment =
                paymentRepository.save(payment);

        paymentHistoryService.saveHistory(
                updatedPayment,
                PaymentStatus.CANCELLED,
                "Payment cancelled."
        );

        return paymentMapper.toResponse(updatedPayment);
    }

    private void requireOwner(Payment payment, Long authenticatedUserId) {
        if (!authenticatedUserId.equals(payment.getUserId())) {
            throw new AccessDeniedException("No tienes acceso a este pago.");
        }
    }

    private PaymentAmountResolution resolveAmountForPayer(
            ReservationResponse reservation,
            Long reservationId,
            Long payerUserId) {

        if (reservation == null || reservation.getIdCarrito() == null || !reservationId.equals(reservation.getIdCarrito())) {
            throw new ReservationAmountResolutionException("Reservation-Service devolvio una reserva distinta a la solicitada.");
        }

        if (reservation.getMoneda() == null || reservation.getMoneda().isBlank()) {
            throw new ReservationAmountResolutionException("La reserva no define una moneda unica para calcular el pago.");
        }

        if (reservation.getAsientos() == null || reservation.getAsientos().isEmpty()) {
            throw new ReservationAmountResolutionException("La reserva no tiene importes pendientes para pagar.");
        }

        BigDecimal amount = reservation.getAsientos().stream()
                .filter(detail -> payerUserId.equals(detail.getPagadorId()))
                .filter(detail -> "PENDIENTE".equalsIgnoreCase(detail.getEstadoPago()))
                .map(ReservationResponse.SeatDetail::getPrecioCobrado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReservationAmountResolutionException("La reserva no tiene un importe pendiente para el usuario indicado.");
        }

        return new PaymentAmountResolution(amount, reservation.getMoneda().trim().toUpperCase());
    }

    private record PaymentAmountResolution(
            BigDecimal amount,
            String currency) {
    }
}