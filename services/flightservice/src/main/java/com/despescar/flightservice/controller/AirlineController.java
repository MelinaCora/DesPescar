package com.despescar.flightservice.controller;

import com.despescar.flightservice.dto.airlines.request.AirlineRequest;
import com.despescar.flightservice.dto.airlines.response.AirlineResponse;
import com.despescar.flightservice.service.AirlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/airlines")
@RequiredArgsConstructor
public class AirlineController {

    private final AirlineService airlineService;

    /**
     * Crear una aerolínea.
     */
    @PostMapping
    public ResponseEntity<AirlineResponse> createAirline(
            @RequestBody AirlineRequest request) {

        AirlineResponse response = airlineService.createAirline(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Obtener todas las aerolíneas.
     */
    @GetMapping
    public ResponseEntity<List<AirlineResponse>> getAllAirlines() {

        return ResponseEntity.ok(
                airlineService.getAllAirlines()
        );
    }

    /**
     * Obtener una aerolínea por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AirlineResponse> getAirlineById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                airlineService.getAirlineById(id)
        );
    }

    /**
     * Obtener una aerolínea por código.
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<AirlineResponse> getAirlineByCode(
            @PathVariable String code) {

        return ResponseEntity.ok(
                airlineService.getAirlineByCode(code)
        );
    }

    /**
     * Actualizar una aerolínea.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AirlineResponse> updateAirline(
            @PathVariable UUID id,
            @RequestBody AirlineRequest request) {

        return ResponseEntity.ok(
                airlineService.updateAirline(id, request)
        );
    }

    /**
     * Eliminar una aerolínea.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAirline(
            @PathVariable UUID id) {

        airlineService.deleteAirline(id);

        return ResponseEntity.noContent().build();
    }
}