package com.despescar.flightservice.controller;

import com.despescar.flightservice.dto.flights.request.FlightRequest;
import com.despescar.flightservice.dto.flights.response.FlightResponse;
import com.despescar.flightservice.dto.flights.response.FlightSearchResponse;
import com.despescar.flightservice.service.FlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
       // List<FlightResponse> flights = flightService.getAllFlights();
        // 200 OK: la petición se procesó correctamente.
       // return new ResponseEntity<>(flights, HttpStatus.OK);
        return ResponseEntity.ok(
                flightService.getAllFlights()
        );
    }

    @GetMapping("/search")
    public ResponseEntity<FlightSearchResponse> searchFlights(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            @RequestParam int passengers) {

        FlightSearchResponse response = flightService.searchFlights(origin, destination, departureDate, returnDate, passengers);
        return ResponseEntity.ok(response);
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

    /**
     * Ajustar asientos disponibles (uso interno del reservation-service).
     * delta negativo para reservar asientos, positivo para liberarlos.
     */
    @PatchMapping("/number/{flightId}/seats")
    public ResponseEntity<Void> adjustSeats(
            @PathVariable UUID flightId,
            @RequestParam int delta) {

        flightService.adjustSeats(flightId, delta); // Asegúrate de que tu servicio interno soporte UUID
        return ResponseEntity.noContent().build();
    }
}