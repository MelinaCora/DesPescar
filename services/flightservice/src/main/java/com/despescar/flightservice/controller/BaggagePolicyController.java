package com.despescar.flightservice.controller;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.despescar.flightservice.dto.baggage.response.BaggagePolicyResponse;
import com.despescar.flightservice.dto.baggage.request.BaggagePolicyRequest;
import com.despescar.flightservice.service.BaggagePolicyService;

import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/baggage-policies")
@RequiredArgsConstructor
public class BaggagePolicyController {

    private final BaggagePolicyService baggagePolicyService;



    @PostMapping
    public ResponseEntity<BaggagePolicyResponse> create(
            @RequestBody BaggagePolicyRequest request) {


        BaggagePolicyResponse response =
                baggagePolicyService.create(request);


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }



    @GetMapping
    public ResponseEntity<List<BaggagePolicyResponse>> findAll() {


        return ResponseEntity.ok(
                baggagePolicyService.findAll()
        );
    }



    @GetMapping("/{id}")
    public ResponseEntity<BaggagePolicyResponse> findById(
            @PathVariable UUID id) {


        return ResponseEntity.ok(
                baggagePolicyService.findById(id)
        );
    }
}
