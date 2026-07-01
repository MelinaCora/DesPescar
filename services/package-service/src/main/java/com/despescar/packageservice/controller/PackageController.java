package com.despescar.packageservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
 class PackageController {
	
	@GetMapping("/test")
    public String test() {
        return "Package Service funcionando correctamente";

	}
}
