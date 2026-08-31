package com.despescar.flightservice.controller;

import com.despescar.flightservice.dto.airports.request.AirportRequest;
import com.despescar.flightservice.dto.airports.response.AirportResponse;
import com.despescar.flightservice.service.AirportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/airports")
@RequiredArgsConstructor
public class AirportController {

    private final AirportService airportService;

    /**
     * Crear un aeropuerto.
     */
    @PostMapping
    public ResponseEntity<AirportResponse> createAirport(
            @Valid @RequestBody AirportRequest request) {

        AirportResponse response = airportService.createAirport(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Obtener todos los aeropuertos.
     */
    @GetMapping
    public ResponseEntity<List<AirportResponse>> getAllAirports() {

        return ResponseEntity.ok(
                airportService.getAllAirports()
        );
    }

    /**
     * Obtener aeropuerto por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AirportResponse> getAirportById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                airportService.getAirportById(id)
        );
    }

    /**
     * Obtener aeropuerto por código IATA.
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<AirportResponse> getAirportByCode(
            @PathVariable String code) {

        return ResponseEntity.ok(
                airportService.getAirportByCode(code)
        );
    }

    /**
     * Obtener aeropuertos por país.
     */
    @GetMapping("/country/{country}")
    public ResponseEntity<List<AirportResponse>> getAirportsByCountry(
            @PathVariable String country) {

        return ResponseEntity.ok(
                airportService.getAirportsByCountry(country)
        );
    }

    /**
     * Obtener aeropuertos por ciudad.
     */
    @GetMapping("/city/{city}")
    public ResponseEntity<List<AirportResponse>> getAirportsByCity(
            @PathVariable String city) {

        return ResponseEntity.ok(
                airportService.getAirportsByCity(city)
        );
    }

    /**
     * Actualizar un aeropuerto.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AirportResponse> updateAirport(
            @PathVariable UUID id,
            @Valid @RequestBody AirportRequest request) {

        return ResponseEntity.ok(
                airportService.updateAirport(id, request)
        );
    }

    /**
     * Eliminar un aeropuerto.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAirport(
            @PathVariable UUID id) {

        airportService.deleteAirport(id);

        return ResponseEntity.noContent().build();
    }

}