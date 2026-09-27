package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/{flightId}/seats")
    public ResponseEntity<List<Seat>> fetchSeatByFlight(@PathVariable UUID flightId) {
        return ResponseEntity.ok(seatService.fetchSeatByFlight(flightId));
    }

}
