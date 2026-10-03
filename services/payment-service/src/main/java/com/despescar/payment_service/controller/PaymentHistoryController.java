package com.despescar.payment_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.despescar.payment_service.dto.response.PaymentHistoryResponse;
import com.despescar.payment_service.service.PaymentHistoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payment-history")
@RequiredArgsConstructor
public class PaymentHistoryController {

    private final PaymentHistoryService paymentHistoryService;

    /**
     * Gets the complete history of a payment.
     */
    @GetMapping("/payment/{paymentId}")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<List<PaymentHistoryResponse>> getHistoryByPayment(
            @PathVariable UUID paymentId,
            Authentication authentication) {

        List<PaymentHistoryResponse> response =
                paymentHistoryService.getHistoryByPayment(paymentId, Long.valueOf(authentication.getName()));

        return ResponseEntity.ok(response);
    }
}

