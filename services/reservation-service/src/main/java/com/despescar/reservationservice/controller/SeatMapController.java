package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.reservation.response.FlightSeatMapResponse;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.mapper.SeatMapMapper;
import com.despescar.reservationservice.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings/flights")
@RequiredArgsConstructor
public class SeatMapController {

    private final SeatRepository seatRepository;
    private final SeatMapMapper seatMapMapper;

    @GetMapping("/{flightId}/seat-map")
    public ResponseEntity<FlightSeatMapResponse> getFlightSeatMap(
            @PathVariable UUID flightId,
            @RequestParam(defaultValue = "4") int selectionLimit
    ) {
        List<Seat> asientos = seatRepository.findByFlightId(flightId);

        if (asientos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FlightSeatMapResponse response = seatMapMapper.toSeatMapResponse(asientos, selectionLimit);

        return ResponseEntity.ok(response);
    }
}