package com.despescar.payment_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.despescar.payment_service.dto.request.MercadoPagoWebhookRequest;

@RestController
@RequestMapping("/api/payments/mercadopago")
public class MercadoPagoWebhookController {

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody MercadoPagoWebhookRequest request) {

        System.out.println(
                "Mercado Pago webhook received"
        );

        System.out.println(
                "Type: " + request.getType()
        );

        System.out.println(
                "Payment ID: " + request.getData().getId()
        );

        return ResponseEntity.ok().build();
    }
}



