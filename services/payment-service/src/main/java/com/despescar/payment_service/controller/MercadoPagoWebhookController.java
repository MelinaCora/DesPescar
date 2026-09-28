package com.despescar.payment_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.despescar.payment_service.dto.request.MercadoPagoWebhookRequest;
import com.despescar.payment_service.service.MercadoPagoWebhookService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments/mercadopago")
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final MercadoPagoWebhookService mercadoPagoWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody MercadoPagoWebhookRequest request) {
        mercadoPagoWebhookService.processPaymentNotification(
                request.getData().getId()
        );

        return ResponseEntity.ok().build();
    }
}


