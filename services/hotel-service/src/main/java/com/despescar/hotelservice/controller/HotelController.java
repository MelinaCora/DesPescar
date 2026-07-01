package com.despescar.hotelservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HotelController {
	
	@GetMapping("/test")
    public String test() {
        return "Hotel Service funcionando correctamente";

	}
}
