package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.passengers.request.PassengerRequest;
import com.despescar.reservationservice.dto.reservation.request.CreateReservationRequest;
import com.despescar.reservationservice.dto.reservation.request.ProcessPaymentRequest;
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

    @PostMapping
    public ResponseEntity<ReservationResponse> crearReserva(
            @RequestBody CreateReservationRequest dto,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        ReservationResponse respuesta = bookingService.crearReserva(dto, authorizationHeader);
        return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
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

    @PatchMapping("/{id}/passenger")
    public ResponseEntity<Void> updatePassenger(
            @PathVariable Long id,
            @RequestBody PassengerRequest dto) {


        passengerService.updatePassengerData(id, dto);


        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<String> procesarPago(
            @PathVariable Long id,
            @RequestBody ProcessPaymentRequest dto) {
        String mensaje = bookingService.procesarPago(id, dto);
        return ResponseEntity.ok(mensaje);
    }

}
