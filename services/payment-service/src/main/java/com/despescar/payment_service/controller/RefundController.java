package com.despescar.payment_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<RefundResponse> createRefund(
            @Valid @RequestBody RefundRequest request) {

        RefundResponse response = refundService.createRefund(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Gets a refund by ID.
     */
    @GetMapping("/{refundId}")
    public ResponseEntity<RefundResponse> getRefundById(
            @PathVariable UUID refundId) {

        RefundResponse response = refundService.getRefundById(refundId);

        return ResponseEntity.ok(response);
    }

    /**
     * Gets all refunds associated with a payment.
     */
    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<List<RefundResponse>> getRefundsByPayment(
            @PathVariable UUID paymentId) {

        List<RefundResponse> response =
                refundService.getRefundsByPayment(paymentId);

        return ResponseEntity.ok(response);
    }

    /**
     * Gets all refunds associated with a user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RefundResponse>> getRefundsByUser(
            @PathVariable UUID userId) {

        List<RefundResponse> response =
                refundService.getRefundsByUser(userId);

        return ResponseEntity.ok(response);
    }
}

