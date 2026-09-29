package com.despescar.payment_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.despescar.payment_service.dto.request.MercadoPagoWebhookRequest;
import com.despescar.payment_service.service.MercadoPagoWebhookService;
import com.despescar.payment_service.service.MercadoPagoWebhookSignatureValidator;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments/mercadopago")
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final MercadoPagoWebhookService mercadoPagoWebhookService;
    private final MercadoPagoWebhookSignatureValidator mercadoPagoWebhookSignatureValidator;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestParam(name = "data.id", required = false) String queryDataId,
            @RequestParam(name = "type", required = false) String queryType,
            @RequestHeader(name = "x-signature", required = false) String signatureHeader,
            @RequestHeader(name = "x-request-id", required = false) String requestIdHeader,
            @RequestBody(required = false) MercadoPagoWebhookRequest request) {
        String dataId = queryDataId != null ? queryDataId : request != null && request.getData() != null ? request.getData().getId() : null;
        String type = queryType != null ? queryType : request != null ? request.getType() : null;

        mercadoPagoWebhookSignatureValidator.validate(signatureHeader, requestIdHeader, queryDataId);
        mercadoPagoWebhookService.processPaymentNotification(
                dataId,
                type
        );

        return ResponseEntity.ok().build();
    }
}

