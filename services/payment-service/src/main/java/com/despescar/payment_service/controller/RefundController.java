package com.despescar.payment_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.despescar.payment_service.dto.request.RefundRequest;
import com.despescar.payment_service.dto.response.RefundResponse;
import com.despescar.payment_service.service.RefundService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * Creates a new refund.
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<RefundResponse> createRefund(
            @Valid @RequestBody RefundRequest request,
            Authentication authentication) {

        RefundResponse response = refundService.createRefund(request, Long.valueOf(authentication.getName()));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Gets a refund by ID.
     */
    @GetMapping("/{refundId}")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<RefundResponse> getRefundById(
            @PathVariable UUID refundId,
            Authentication authentication) {

        RefundResponse response = refundService.getRefundById(refundId, Long.valueOf(authentication.getName()));

        return ResponseEntity.ok(response);
    }

    /**
     * Gets all refunds associated with a payment.
     */
    @GetMapping("/payment/{paymentId}")
        @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<List<RefundResponse>> getRefundsByPayment(
            @PathVariable UUID paymentId,
            Authentication authentication) {

        List<RefundResponse> response =
                refundService.getRefundsByPayment(paymentId, Long.valueOf(authentication.getName()));

        return ResponseEntity.ok(response);
    }

    /**
     * Gets all refunds associated with a user.
     */
    @GetMapping("/user/{userId}")
        @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<List<RefundResponse>> getRefundsByUser(
            @PathVariable Long userId,
            Authentication authentication) {

        List<RefundResponse> response =
                refundService.getRefundsByUser(userId, Long.valueOf(authentication.getName()));

        return ResponseEntity.ok(response);
    }
}
