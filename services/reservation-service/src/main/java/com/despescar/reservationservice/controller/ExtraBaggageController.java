package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.extraBaggage.request.ExtraBaggageRequest;
import com.despescar.reservationservice.dto.extraBaggage.response.ExtraBaggageResponse;
import com.despescar.reservationservice.service.ExtraBaggageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/extra-baggage")
@RequiredArgsConstructor
public class ExtraBaggageController {

    private final ExtraBaggageService extraBaggageService;


    @PostMapping("/{detalleReservaId}")
    public ResponseEntity<ExtraBaggageResponse> addBaggage(
            @PathVariable Long detalleReservaId,
            @RequestBody ExtraBaggageRequest request
    ){

        ExtraBaggageResponse response =
                extraBaggageService.addBaggage(
                        detalleReservaId,
                        request.getPeso()
                );

        return ResponseEntity.ok(response);
    }
}