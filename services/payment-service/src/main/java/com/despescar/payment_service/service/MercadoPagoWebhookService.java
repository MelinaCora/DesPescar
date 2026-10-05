package com.despescar.payment_service.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Notificaciones de Mercado Pago (la firma ya la valido el controller). Consulta el pago en
 * Mercado Pago y aplica su estado; la conciliacion de /pago/resultado reutiliza aplicarEstado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentHistoryService paymentHistoryService;
    private final AprobacionPagoService aprobacionPagoService;

    @Transactional
    public void processPaymentNotification(String mercadoPagoPaymentId, String notificationType) {

        if (notificationType != null && !"payment".equalsIgnoreCase(notificationType)) {
            return;
        }
        if (paymentGatewayService.provider() != PaymentProvider.MERCADO_PAGO) {
            log.warn("Notificacion de Mercado Pago ignorada: el proveedor activo es {}.", paymentGatewayService.provider());
            return;
        }
        if (mercadoPagoPaymentId == null || mercadoPagoPaymentId.isBlank()) {
            return;
        }

        PaymentGatewayResponse gatewayResponse = paymentGatewayService.getPaymentStatus(mercadoPagoPaymentId);

        Optional<Payment> payment = parseUuid(gatewayResponse.getExternalReference())
                .flatMap(paymentRepository::findByIdParaActualizar)
                .filter(p -> p.getProvider() == PaymentProvider.MERCADO_PAGO);
        if (payment.isEmpty()) {
            log.warn("Pago {} de Mercado Pago con referencia desconocida: {}",
                    mercadoPagoPaymentId, gatewayResponse.getExternalReference());
            return;
        }

        aplicarEstado(payment.get(), gatewayResponse);
    }

    /**
     * Llamar con el pago leido por {@code PaymentRepository.findByIdParaActualizar}, dentro de la
     * transaccion que lo bloqueo.
     * Aplica al pago el estado informado por Mercado Pago. approved confirma la reserva (una sola
     * vez); refunded lo marca REFUNDED; rejected/cancelled solo cambian un pago que no se cobro;
     * pending, in_process y authorized no cambian nada.
     */
    @Transactional
    public Payment aplicarEstado(Payment payment, PaymentGatewayResponse gatewayResponse) {
        PaymentStatus nuevo = mapPaymentStatus(gatewayResponse.getStatus());
        PaymentStatus actual = payment.getStatus();

        switch (nuevo) {
            case APPROVED -> {
                String monedaCobrada = gatewayResponse.getCurrency();
                if (monedaCobrada != null && !"ARS".equalsIgnoreCase(monedaCobrada)) {
                    log.warn("Mercado Pago aprobo el pago {} en {}: no se aprueba solo, requiere revision manual.",
                            payment.getId(), monedaCobrada);
                    paymentHistoryService.saveHistory(payment, actual,
                            "Revision manual: moneda " + monedaCobrada + " en lugar de ARS (cobro "
                                    + gatewayResponse.getTransactionId() + ").");
                    return payment;
                }
                if ((actual == PaymentStatus.APPROVED || actual == PaymentStatus.REFUNDED)
                        && esOtroCobro(payment, gatewayResponse)) {
                    log.warn("Segundo cobro {} aprobado para el pago {}: se reembolsa.",
                            gatewayResponse.getTransactionId(), payment.getId());
                    aprobacionPagoService.reembolsarCobroDuplicado(
                            payment, gatewayResponse.getTransactionId(), gatewayResponse.getAmount());
                    return payment;
                }
                if (gatewayResponse.getAmount() != null
                        && gatewayResponse.getAmount().compareTo(payment.getAmount()) != 0) {
                    log.warn("Mercado Pago cobro {} por el pago {} de {}; lo decide reservation-service.",
                            gatewayResponse.getAmount(), payment.getId(), payment.getAmount());
                }
                return aprobacionPagoService.aprobar(payment, gatewayResponse.getTransactionId(),
                        resolvePaymentMethod(gatewayResponse), gatewayResponse.getAmount());
            }
            case REFUNDED -> {
                boolean delMismoCobro = payment.getTransactionId() == null
                        || payment.getTransactionId().equals(gatewayResponse.getTransactionId());
                if (actual == PaymentStatus.REFUNDED || !delMismoCobro) {
                    return payment;
                }
                payment.setStatus(PaymentStatus.REFUNDED);
                Payment guardado = paymentRepository.save(payment);
                paymentHistoryService.saveHistory(guardado, PaymentStatus.REFUNDED,
                        "Mercado Pago informo el pago como reembolsado.");
                return guardado;
            }
            case REJECTED, CANCELLED -> {
                boolean sinCobrar = actual == PaymentStatus.PENDING || actual == PaymentStatus.REJECTED;
                boolean mismo = actual == nuevo
                        && Objects.equals(payment.getTransactionId(), gatewayResponse.getTransactionId());
                if (!sinCobrar || mismo) {
                    return payment;
                }
                payment.setStatus(nuevo);
                payment.setTransactionId(gatewayResponse.getTransactionId());
                Payment guardado = paymentRepository.save(payment);
                paymentHistoryService.saveHistory(guardado, nuevo,
                        "Mercado Pago informo el pago como " + gatewayResponse.getStatus() + ".");
                return guardado;
            }
            default -> {
                return payment;
            }
        }
    }

    private static boolean esOtroCobro(Payment payment, PaymentGatewayResponse gatewayResponse) {
        return payment.getTransactionId() != null && gatewayResponse.getTransactionId() != null
                && !payment.getTransactionId().equals(gatewayResponse.getTransactionId());
    }

    private PaymentStatus mapPaymentStatus(String mercadoPagoStatus) {

        if (mercadoPagoStatus == null || mercadoPagoStatus.isBlank()) {
            return PaymentStatus.PENDING;
        }

        return switch (mercadoPagoStatus.toLowerCase()) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected" -> PaymentStatus.REJECTED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            case "refunded", "charged_back" -> PaymentStatus.REFUNDED;
            case "authorized" -> PaymentStatus.AUTHORIZED;
            default -> PaymentStatus.PENDING;
        };
    }

    private PaymentMethod resolvePaymentMethod(PaymentGatewayResponse gatewayResponse) {
        if (gatewayResponse.getPaymentTypeId() == null) {
            return null;
        }

        return switch (gatewayResponse.getPaymentTypeId().toLowerCase()) {
            case "credit_card" -> PaymentMethod.CREDIT_CARD;
            case "debit_card" -> PaymentMethod.DEBIT_CARD;
            case "bank_transfer" -> PaymentMethod.BANK_TRANSFER;
            case "account_money" -> PaymentMethod.DIGITAL_WALLET;
            default -> null;
        };
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
