package com.despescar.payment_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
	class PaymentController {
	
	@GetMapping("/test")
    public String test() {
        return "Payment Service funcionando correctamente";

	}
}
