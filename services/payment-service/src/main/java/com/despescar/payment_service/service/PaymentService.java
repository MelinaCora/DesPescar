package com.despescar.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String PENDIENTE_PAGO = "PENDIENTE_PAGO";
    private static final String MONEDA = "ARS";

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentHistoryService paymentHistoryService;
    private final PaymentGatewayService paymentGatewayService;
    private final ReservationClient reservationClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * Crea el pago del carrito completo. El monto es el montoTotal que calcula reservation-service
     * (nunca el del cliente); solo paga el creador y solo con la reserva en PENDIENTE_PAGO sin vencer.
     * Un doble clic en Pagar devuelve el mismo pago PENDING (D19), tambien si los dos pedidos llegan
     * a la vez: el indice unico de pendienteDeReserva deja pasar un solo insert y el otro relee el
     * pago ganador. El pago se confirma en la base antes de llamar al proveedor, asi la llamada
     * remota no sostiene una transaccion.
     */
    public PaymentResponse createPayment(PaymentRequest request, Long authenticatedUserId) {
        Long reservationId = request.getReservationId();
        ReservationResponse reservation = reservationClient.getReservation(reservationId);
        BigDecimal amount = montoAPagar(reservation, reservationId, authenticatedUserId);
        PaymentProvider provider = paymentGatewayService.provider();

        Preparado preparado;
        try {
            preparado = transactionTemplate.execute(
                    status -> reservarPago(request, authenticatedUserId, amount, provider));
        } catch (DataIntegrityViolationException ex) {
            // Otro pedido inserto antes el PENDING de esta reserva: se relee y se reutiliza.
            preparado = transactionTemplate.execute(
                    status -> reservarPago(request, authenticatedUserId, amount, provider));
        }

        Payment pago = preparado.pago();
        if (!preparado.requiereCheckout()) {
            return paymentMapper.toResponse(pago);
        }

        PaymentCheckoutResponse checkout = paymentGatewayService.createCheckout(
                pago.getId().toString(), pago.getAmount(), pago.getCurrency());

        return transactionTemplate.execute(status -> {
            Payment actual = paymentRepository.findById(pago.getId())
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + pago.getId()));
            actual.setPreferenceId(checkout.getPreferenceId());
            actual.setCheckoutUrl(checkout.getCheckoutUrl());
            return paymentMapper.toResponse(paymentRepository.save(actual));
        });
    }

    /** Pago a devolver y si todavia hay que pedirle el checkout al proveedor. */
    private record Preparado(Payment pago, boolean requiereCheckout) {
    }

    /** Descarta los PENDING viejos, reutiliza el vigente o inserta el nuevo PENDING, todo en una transaccion. */
    private Preparado reservarPago(
            PaymentRequest request, Long userId, BigDecimal amount, PaymentProvider provider) {

        Payment reusable = descartarPendientesAnteriores(request.getReservationId(), userId, amount, provider);
        if (reusable != null) {
            // Sin checkoutUrl es un pago cuyo checkout no llego a pedirse (caida entre los dos pasos).
            return new Preparado(reusable, reusable.getCheckoutUrl() == null);
        }

        Payment payment = paymentMapper.toEntity(request, amount, MONEDA, userId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(provider);

        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        paymentHistoryService.saveHistory(
                savedPayment,
                PaymentStatus.PENDING,
                "Payment created and is pending."
        );
        return new Preparado(savedPayment, true);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId, Long authenticatedUserId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));

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
    public List<PaymentResponse> getPaymentsByReservation(Long reservationId, Long authenticatedUserId) {

        return paymentRepository.findByReservationId(reservationId)
                .stream()
                .filter(payment -> authenticatedUserId.equals(payment.getUserId()))
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional
    public PaymentResponse cancelPayment(UUID paymentId, Long authenticatedUserId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));

        requireOwner(payment, authenticatedUserId);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    "Payment cannot be cancelled because its current status is: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.CANCELLED);

        Payment updatedPayment = paymentRepository.save(payment);

        paymentHistoryService.saveHistory(updatedPayment, PaymentStatus.CANCELLED, "Payment cancelled.");

        return paymentMapper.toResponse(updatedPayment);
    }

    private void requireOwner(Payment payment, Long authenticatedUserId) {
        if (!authenticatedUserId.equals(payment.getUserId())) {
            throw new AccessDeniedException("No tienes acceso a este pago.");
        }
    }

    /** Valida que el carrito se pueda pagar y devuelve su montoTotal en escala 2. */
    private BigDecimal montoAPagar(ReservationResponse reservation, Long reservationId, Long payerUserId) {

        if (reservation == null || reservation.getIdCarrito() == null || !reservationId.equals(reservation.getIdCarrito())) {
            throw new ReservationAmountResolutionException("Reservation-Service devolvio una reserva distinta a la solicitada.");
        }
        if (!payerUserId.equals(reservation.getCreadorId())) {
            throw new AccessDeniedException("Solo quien creo el carrito puede pagarlo.");
        }
        if (!PENDIENTE_PAGO.equals(reservation.getEstadoGeneral())) {
            throw new InvalidPaymentStateException(
                    "La reserva no esta lista para pagar (estado " + reservation.getEstadoGeneral()
                            + "). Completa los datos del carrito.");
        }
        if (reservation.getSegundosRestantes() == null || reservation.getSegundosRestantes() <= 0) {
            throw new InvalidPaymentStateException("El carrito vencio. Volve a armarlo para pagar.");
        }
        if (reservation.getMoneda() == null || reservation.getMoneda().isBlank()) {
            throw new ReservationAmountResolutionException("La reserva no define una moneda unica para calcular el pago.");
        }
        if (!MONEDA.equalsIgnoreCase(reservation.getMoneda().trim())) {
            throw new ReservationAmountResolutionException("Solo se cobra en pesos argentinos (ARS).");
        }
        if (reservation.getMontoTotal() == null || reservation.getMontoTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReservationAmountResolutionException("La reserva no tiene un importe para pagar.");
        }
        return reservation.getMontoTotal().setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Devuelve el pago PENDING del usuario con el mismo monto y proveedor, si existe. Los demas
     * PENDING del usuario para esa reserva (el carrito cambio de total) pasan a CANCELLED.
     */
    private Payment descartarPendientesAnteriores(
            Long reservationId, Long userId, BigDecimal amount, PaymentProvider provider) {

        Payment reusable = null;
        for (Payment previo : paymentRepository.findByReservationId(reservationId)) {
            if (previo.getStatus() != PaymentStatus.PENDING || !userId.equals(previo.getUserId())) {
                continue;
            }
            boolean mismoPago = previo.getProvider() == provider
                    && previo.getAmount() != null && previo.getAmount().compareTo(amount) == 0;
            if (mismoPago && reusable == null) {
                reusable = previo;
                continue;
            }
            previo.setStatus(PaymentStatus.CANCELLED);
            // flush: el indice de pendienteDeReserva tiene que quedar libre antes de insertar el nuevo
            paymentRepository.saveAndFlush(previo);
            paymentHistoryService.saveHistory(previo, PaymentStatus.CANCELLED,
                    "Reemplazado por un pago nuevo: cambio el total del carrito.");
        }
        return reusable;
    }
}
