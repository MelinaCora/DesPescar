package com.despescar.payment_service.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.OperacionNoDisponibleException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Conciliacion desde /pago/resultado (D16): con el payment_id que Mercado Pago agrega a la
 * back_url se consulta el pago y se aplica su estado, igual que el webhook, sin depender de que
 * el webhook llegue (en local no hay URL publica).
 */
@Service
@RequiredArgsConstructor
public class PaymentConciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final MercadoPagoWebhookService mercadoPagoWebhookService;
    private final PaymentMapper paymentMapper;

    @Transactional
    public PaymentResponse conciliar(UUID paymentId, String mpPaymentId, Long authenticatedUserId) {
        if (paymentGatewayService.provider() != PaymentProvider.MERCADO_PAGO) {
            throw new OperacionNoDisponibleException("La conciliacion solo existe con Mercado Pago.");
        }

        Payment payment = paymentRepository.findByIdParaActualizar(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        if (!authenticatedUserId.equals(payment.getUserId())) {
            throw new AccessDeniedException("No tienes acceso a este pago.");
        }
        if (payment.getProvider() != PaymentProvider.MERCADO_PAGO) {
            throw new OperacionNoDisponibleException("Este pago no se creo con Mercado Pago.");
        }

        PaymentGatewayResponse gatewayResponse = paymentGatewayService.getPaymentStatus(mpPaymentId);
        if (!payment.getId().toString().equals(gatewayResponse.getExternalReference())) {
            throw new InvalidPaymentStateException("El pago de Mercado Pago no corresponde a este pago.");
        }

        return paymentMapper.toResponse(mercadoPagoWebhookService.aplicarEstado(payment, gatewayResponse));
    }
}
