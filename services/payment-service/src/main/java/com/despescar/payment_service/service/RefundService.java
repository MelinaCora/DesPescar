package com.despescar.payment_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.despescar.payment_service.dto.request.RefundRequest;
import com.despescar.payment_service.dto.response.RefundResponse;

@Service
public class RefundService {

    public RefundResponse createRefund(RefundRequest request) {
        return null;
    }

    public RefundResponse getRefundById(UUID refundId) {
        return null;
    }

    public List<RefundResponse> getRefundsByPayment(UUID paymentId) {
        return null;
    }

    public List<RefundResponse> getRefundsByUser(UUID userId) {
        return null;
    }

}