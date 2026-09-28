package com.despescar.payment_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
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

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        // 1. Crear el pago en DesPescar
        Payment payment = paymentMapper.toEntity(request);

        // 2. El pago comienza como PENDING
        payment.setStatus(PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.save(payment);

        // 3. Registrar historial
        paymentHistoryService.saveHistory(
                savedPayment,
                PaymentStatus.PENDING,
                "Payment created and is pending."
        );

        // 4. Crear checkout en Mercado Pago
        PaymentCheckoutResponse checkout =
                paymentGatewayService.createCheckout(
                        savedPayment.getId().toString(),
                        savedPayment.getAmount(),
                        savedPayment.getCurrency(),
                        savedPayment.getPaymentMethod()
                );

        // 5. Guardar Preference ID de Mercado Pago
        savedPayment.setPreferenceId(
                checkout.getPreferenceId()
        );

        Payment updatedPayment =
                paymentRepository.save(savedPayment);

        // 6. Construir respuesta
        PaymentResponse response =
                paymentMapper.toResponse(updatedPayment);

        // 7. Agregar URL del checkout
        response.setCheckoutUrl(
                checkout.getCheckoutUrl()
        );

        return response;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + paymentId
                        ));

        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByUser(UUID userId) {

        return paymentRepository.findByUserId(userId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByReservation(
            UUID reservationId) {

        return paymentRepository.findByReservationId(reservationId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional
    public PaymentResponse cancelPayment(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + paymentId
                        ));

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
}