package com.despescar.flightservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.despescar.flightservice.dto.baggage.response.FareResponse;
import com.despescar.flightservice.dto.baggage.request.FareRequest;
import com.despescar.flightservice.service.FareService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fares")
@RequiredArgsConstructor
public class FareController {

    private final FareService fareService;

    @PostMapping
    public ResponseEntity<FareResponse> create(@RequestBody FareRequest request) {
        FareResponse response = fareService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<FareResponse>> findAll() {
        return ResponseEntity.ok(
                fareService.getAllFares()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<FareResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                fareService.getFareById(id)
        );
    }
}
