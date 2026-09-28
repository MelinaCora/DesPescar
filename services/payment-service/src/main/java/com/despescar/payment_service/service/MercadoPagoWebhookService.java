package com.despescar.payment_service.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.entity.Payment;
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

    @Transactional
    public void processPaymentNotification(String mercadoPagoPaymentId) {

        PaymentGatewayResponse gatewayResponse =
                paymentGatewayService.getPaymentStatus(
                        mercadoPagoPaymentId
                );

        UUID paymentId =
                UUID.fromString(
                        gatewayResponse.getTransactionId()
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

        if (payment.getStatus() == newStatus) {
            return;
        }

        payment.setStatus(newStatus);

        if (newStatus == PaymentStatus.APPROVED) {
            payment.setTransactionId(
                    mercadoPagoPaymentId
            );

            payment.setPaymentDate(
                    LocalDateTime.now()
            );
        }

        Payment updatedPayment =
                paymentRepository.save(payment);

        paymentHistoryService.saveHistory(
                updatedPayment,
                newStatus,
                "Payment status updated from Mercado Pago."
        );
    }

    private PaymentStatus mapPaymentStatus(String mercadoPagoStatus) {

        return switch (mercadoPagoStatus.toLowerCase()) {

            case "approved" ->
                    PaymentStatus.APPROVED;

            case "rejected",
                 "cancelled" ->
                    PaymentStatus.REJECTED;

            case "pending",
                 "in_process",
                 "in_mediation" ->
                    PaymentStatus.PENDING;

            default ->
                    PaymentStatus.PENDING;
        };
    }
}

