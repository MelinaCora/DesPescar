package com.despescar.packageservice.controller;

import com.despescar.packageservice.dto.request.TravelPackageRequest;
import com.despescar.packageservice.dto.response.TravelPackageResponse;
import com.despescar.packageservice.service.TourPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class PackageController {

    private final TourPackageService tourPackageService;

    @PostMapping
    public ResponseEntity<TravelPackageResponse> create(@Valid @RequestBody TravelPackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tourPackageService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TravelPackageResponse>> list(
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return ResponseEntity.ok(tourPackageService.search(destination, active, minPrice, maxPrice));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TravelPackageResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tourPackageService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TravelPackageResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TravelPackageRequest request
    ) {
        return ResponseEntity.ok(tourPackageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tourPackageService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<TravelPackageResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(tourPackageService.activate(id));
    }
}
