package com.despescar.payment_service.service;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.request.OrdenPagoRequest;
import com.despescar.payment_service.dto.response.OrdenPagoResponse;
import com.despescar.payment_service.dto.response.PaymentConfigResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.OperacionNoDisponibleException;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Cobro con Mercado Pago Checkout API (Orders) desde /pago/mercadopago: con el token de la tarjeta
 * crea la orden y aplica su resultado por el mismo camino que el webhook (aprobada: confirma la
 * reserva y reembolsa si reservation-service la rechaza; rechazada: REJECTED; en proceso: sigue
 * PENDING y lo resuelven el webhook o la conciliacion).
 */
@Service
@Slf4j
public class MercadoPagoOrdenService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final ObjectProvider<MercadoPagoOrdenGateway> ordenGateway;
    private final PaymentHistoryService paymentHistoryService;
    private final MercadoPagoWebhookService mercadoPagoWebhookService;
    private final PaymentMapper paymentMapper;

    public MercadoPagoOrdenService(
            PaymentRepository paymentRepository,
            PaymentGatewayService paymentGatewayService,
            ObjectProvider<MercadoPagoOrdenGateway> ordenGateway,
            PaymentHistoryService paymentHistoryService,
            MercadoPagoWebhookService mercadoPagoWebhookService,
            PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.ordenGateway = ordenGateway;
        this.paymentHistoryService = paymentHistoryService;
        this.mercadoPagoWebhookService = mercadoPagoWebhookService;
        this.paymentMapper = paymentMapper;
    }

    public PaymentConfigResponse configuracion() {
        MercadoPagoOrdenGateway gateway = ordenGateway.getIfAvailable();
        return new PaymentConfigResponse(
                paymentGatewayService.provider(), gateway == null ? null : gateway.publicKey());
    }

    @Transactional
    public OrdenPagoResponse cobrar(UUID paymentId, OrdenPagoRequest request, Long authenticatedUserId) {
        MercadoPagoOrdenGateway gateway = ordenGateway.getIfAvailable();
        if (gateway == null || paymentGatewayService.provider() != PaymentProvider.MERCADO_PAGO_ORDERS) {
            throw new OperacionNoDisponibleException("El cobro con tarjeta solo existe con Mercado Pago (Orders).");
        }

        Payment payment = paymentRepository.findByIdParaActualizar(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        if (!authenticatedUserId.equals(payment.getUserId())) {
            throw new AccessDeniedException("No tienes acceso a este pago.");
        }
        if (payment.getProvider() != PaymentProvider.MERCADO_PAGO_ORDERS) {
            throw new OperacionNoDisponibleException("Este pago no se creo con Mercado Pago (Orders).");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException("El pago ya fue procesado: " + payment.getStatus());
        }

        OrdenMercadoPago orden;
        if (payment.getTransactionId() != null) {
            // Ya hay una orden en proceso para este pago: no se cobra de nuevo, se relee su estado.
            orden = gateway.consultarOrden(payment.getTransactionId());
        } else {
            orden = gateway.crearOrden(new MercadoPagoOrdenGateway.CrearOrden(
                    payment.getId().toString(), payment.getAmount(), request.token(),
                    request.paymentMethodId(), request.tipo(), request.cuotas(), request.payerEmail()));
            log.info("Orden {} creada para el pago {}: {}/{}", orden.id(), payment.getId(),
                    orden.status(), orden.detalle());
            if (!orden.aprobada() && !orden.rechazada()) {
                payment.setTransactionId(orden.id());
                paymentRepository.save(payment);
                paymentHistoryService.saveHistory(payment, PaymentStatus.PENDING,
                        "Orden " + orden.id() + " de Mercado Pago en proceso (" + orden.status() + "/" + orden.detalle() + ").");
            }
        }

        Payment actualizado = mercadoPagoWebhookService.aplicarEstado(payment, orden.toGatewayResponse());
        return new OrdenPagoResponse(paymentMapper.toResponse(actualizado), orden.status(), orden.detalle());
    }
}
