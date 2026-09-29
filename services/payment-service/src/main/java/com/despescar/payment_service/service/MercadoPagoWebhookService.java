package com.despescar.payment_service.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.PaymentNotFoundException;
import com.despescar.payment_service.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MercadoPagoWebhookService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentHistoryService paymentHistoryService;
    private final ReservationClient reservationClient;

    @Transactional
    public void processPaymentNotification(
            String mercadoPagoPaymentId,
            String notificationType) {

        if (notificationType != null && !"payment".equalsIgnoreCase(notificationType)) {
            return;
        }

        PaymentGatewayResponse gatewayResponse =
                paymentGatewayService.getPaymentStatus(
                        mercadoPagoPaymentId
                );

        UUID paymentId =
                UUID.fromString(
                        gatewayResponse.getExternalReference()
                );

        Payment payment =
                paymentRepository.findById(paymentId)
                        .orElseThrow(() ->
                                new PaymentNotFoundException(
                                        "Payment not found with id: "
                                                + paymentId
                                )
                        );

        PaymentStatus newStatus =
                mapPaymentStatus(
                        gatewayResponse.getStatus()
                );

        PaymentMethod confirmedMethod = resolvePaymentMethod(gatewayResponse);
        boolean alreadyProcessed = payment.getStatus() == newStatus
                && equalsOrNull(payment.getTransactionId(), gatewayResponse.getTransactionId())
                && payment.getPaymentMethod() == confirmedMethod;

        if (alreadyProcessed) {
            return;
        }

        payment.setStatus(newStatus);
        payment.setTransactionId(gatewayResponse.getTransactionId());
        payment.setPaymentMethod(confirmedMethod);

        if (newStatus == PaymentStatus.APPROVED) {
            payment.setPaymentDate(
                    LocalDateTime.now()
            );
        }

        Payment updatedPayment =
                paymentRepository.save(payment);

        if (newStatus == PaymentStatus.APPROVED) {
            reservationClient.markReservationPaymentPaid(
                    updatedPayment.getReservationId(),
                    updatedPayment.getUserId(),
                    gatewayResponse.getTransactionId()
            );
        }

        paymentHistoryService.saveHistory(
                updatedPayment,
                newStatus,
                "Payment status updated from Mercado Pago."
        );
    }

    private PaymentStatus mapPaymentStatus(String mercadoPagoStatus) {

        if (mercadoPagoStatus == null || mercadoPagoStatus.isBlank()) {
            return PaymentStatus.PENDING;
        }

        return switch (mercadoPagoStatus.toLowerCase()) {

            case "approved" ->
                    PaymentStatus.APPROVED;

            case "rejected" ->
                    PaymentStatus.REJECTED;

            case "cancelled" ->
                    PaymentStatus.CANCELLED;

            case "authorized" ->
                    PaymentStatus.AUTHORIZED;

            case "pending",
                 "in_process",
                 "in_mediation" ->
                    PaymentStatus.PENDING;

            default ->
                    PaymentStatus.PENDING;
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

    private boolean equalsOrNull(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
