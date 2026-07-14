package com.despescar.flightservice.controller;

import com.despescar.flightservice.dto.flights.request.FlightRequest;
import com.despescar.flightservice.dto.flights.response.FlightResponse;
import com.despescar.flightservice.service.FlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    /**
     * Crear un vuelo.
     */
    @PostMapping
    public ResponseEntity<FlightResponse> createFlight(
            @RequestBody FlightRequest request) {

        FlightResponse response = flightService.createFlight(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Obtener todos los vuelos.
     */
    @GetMapping
    public ResponseEntity<List<FlightResponse>> getAllFlights() {

        return ResponseEntity.ok(
                flightService.getAllFlights()
        );
    }

    /**
     * Obtener un vuelo por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlightResponse> getFlightById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                flightService.getFlightById(id)
        );
    }

    /**
     * Obtener un vuelo por número.
     */
    @GetMapping("/number/{flightNumber}")
    public ResponseEntity<FlightResponse> getFlightByNumber(
            @PathVariable String flightNumber) {

        return ResponseEntity.ok(
                flightService.getFlightByNumber(flightNumber)
        );
    }

    /**
     * Obtener vuelos por aerolínea.
     */
    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<List<FlightResponse>> getFlightsByAirline(
            @PathVariable UUID airlineId) {

        return ResponseEntity.ok(
                flightService.getFlightsByAirline(airlineId)
        );
    }

    /**
     * Obtener vuelos por aeropuerto de origen.
     */
    @GetMapping("/origin/{airportId}")
    public ResponseEntity<List<FlightResponse>> getFlightsByOrigin(
            @PathVariable UUID airportId) {

        return ResponseEntity.ok(
                flightService.getFlightsByOrigin(airportId)
        );
    }

    /**
     * Obtener vuelos por aeropuerto de destino.
     */
    @GetMapping("/destination/{airportId}")
    public ResponseEntity<List<FlightResponse>> getFlightsByDestination(
            @PathVariable UUID airportId) {

        return ResponseEntity.ok(
                flightService.getFlightsByDestination(airportId)
        );
    }

    /**
     * Actualizar un vuelo.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FlightResponse> updateFlight(
            @PathVariable UUID id,
            @RequestBody FlightRequest request) {

        return ResponseEntity.ok(
                flightService.updateFlight(id, request)
        );
    }

    /**
     * Eliminar un vuelo.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlight(
            @PathVariable UUID id) {

        flightService.deleteFlight(id);

        return ResponseEntity.noContent().build();
    }
}