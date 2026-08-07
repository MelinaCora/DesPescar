package com.despescar.payment_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.entity.PaymentHistory;
import com.despescar.payment_service.enums.PaymentStatus;

@Service
public class PaymentHistoryService {

    public void saveHistory(
            Payment payment,
            PaymentStatus status,
            String description) {

    }

    public List<PaymentHistory> getHistoryByPayment(UUID paymentId) {
        return null;
    }

}