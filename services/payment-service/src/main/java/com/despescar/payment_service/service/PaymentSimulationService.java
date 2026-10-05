package com.despescar.payment_service.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.OperacionNoDisponibleException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/** La pagina /pago/simulado del front aprueba o rechaza un pago del proveedor mock. */
@Service
@RequiredArgsConstructor
public class PaymentSimulationService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentHistoryService paymentHistoryService;
    private final AprobacionPagoService aprobacionPagoService;
    private final PaymentMapper paymentMapper;

    @Transactional
    public PaymentResponse simular(UUID paymentId, boolean aprobado, Long authenticatedUserId) {
        if (paymentGatewayService.provider() != PaymentProvider.MOCK) {
            throw new OperacionNoDisponibleException("El simulador de pagos solo existe con el proveedor mock.");
        }

        Payment payment = paymentRepository.findByIdParaActualizar(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        if (!authenticatedUserId.equals(payment.getUserId())) {
            throw new AccessDeniedException("No tienes acceso a este pago.");
        }
        if (payment.getProvider() != PaymentProvider.MOCK) {
            throw new OperacionNoDisponibleException("Este pago no se creo con el simulador.");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException("El pago ya fue procesado: " + payment.getStatus());
        }

        if (!aprobado) {
            payment.setStatus(PaymentStatus.REJECTED);
            Payment rechazado = paymentRepository.save(payment);
            paymentHistoryService.saveHistory(rechazado, PaymentStatus.REJECTED, "Pago rechazado en el simulador.");
            return paymentMapper.toResponse(rechazado);
        }

        Payment resultado = aprobacionPagoService.aprobar(
                payment, "MOCK-" + payment.getId(), PaymentMethod.CREDIT_CARD, null);
        return paymentMapper.toResponse(resultado);
    }
}
