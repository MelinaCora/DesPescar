package com.despescar.reservationservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReservationController {	

	@GetMapping("/test")
    public String test() {
        return "Reservation Service funcionando correctamente";

	}

}
