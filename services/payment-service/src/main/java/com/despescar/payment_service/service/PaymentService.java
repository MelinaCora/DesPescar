package com.despescar.payment_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentResponse;

@Service
public class PaymentService {

    public PaymentResponse createPayment(PaymentRequest request) {
        return null;
    }

    public PaymentResponse getPaymentById(UUID paymentId) {
        return null;
    }

    public List<PaymentResponse> getPaymentsByUser(UUID userId) {
        return null;
    }

    public List<PaymentResponse> getPaymentsByReservation(UUID reservationId) {
        return null;
    }

    public PaymentResponse cancelPayment(UUID paymentId) {
        return null;
    }

}