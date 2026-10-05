package com.despescar.koiiaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;

@SpringBootApplication
@CrossOrigin(origins = "${cors.allowed-origins}")
public class KoiIaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KoiIaServiceApplication.class, args);
    }
}