package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.passengers.request.PassengerAssignationRequest;
import com.despescar.reservationservice.dto.reservation.request.BookingInitRequest;
import com.despescar.reservationservice.dto.reservation.request.ProcessPaymentRequest;
import com.despescar.reservationservice.dto.reservation.request.SplitPaymentSetupRequest;
import com.despescar.reservationservice.dto.reservation.response.BookingInitResponse;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.service.BookingService;
import com.despescar.reservationservice.service.PassengerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final PassengerService passengerService;

    @PostMapping("/init")
    public ResponseEntity<BookingInitResponse> initBooking(
            @RequestBody BookingInitRequest dto,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        BookingInitResponse respuesta = bookingService.initializeBooking(dto, token);
        return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/passengers")
    public ResponseEntity<Void> assignPassengers(
            @PathVariable Long id,
            @RequestBody PassengerAssignationRequest dto
    ) {
        passengerService.assignPassengersToSeats(id, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/split-setup")
    public ResponseEntity<Void> setupSplitPayment(
            @PathVariable Long id,
            @RequestBody SplitPaymentSetupRequest dto
    ) {
        bookingService.setupSplitPayment(id, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<String> procesarPago(
            @PathVariable Long id,
            @RequestBody ProcessPaymentRequest dto
    ) {
        String mensaje = bookingService.procesarPago(id, dto);
        return ResponseEntity.ok(mensaje);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> obtenerReserva(@PathVariable Long id) {
        ReservationResponse respuesta = bookingService.obtenerReserva(id);
        return ResponseEntity.ok(respuesta);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarReservaManualmente(
            @PathVariable Long id,
            @RequestParam Long usuarioId
    ) {
        bookingService.cancelarReservaManualmente(id, usuarioId);
        return ResponseEntity.ok().build();
    }
}