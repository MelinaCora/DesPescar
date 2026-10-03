package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.passengers.request.PassengerAssignationRequest;
import com.despescar.reservationservice.dto.reservation.request.BookingInitRequest;
import com.despescar.reservationservice.dto.reservation.request.PaymentConfirmationRequest;
import com.despescar.reservationservice.dto.reservation.request.SplitPaymentSetupRequest;
import com.despescar.reservationservice.dto.reservation.response.BookingInitResponse;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.service.BookingService;
import com.despescar.reservationservice.service.PassengerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final PassengerService passengerService;

    @PostMapping("/init")
        @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<BookingInitResponse> initBooking(
            @RequestBody BookingInitRequest dto,
            @RequestHeader(value = "Authorization", required = false) String token,
            Authentication authentication
    ) {
        BookingInitResponse respuesta = bookingService.initializeBooking(
            dto,
            token,
            Long.valueOf(authentication.getName())
        );
        return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/passengers")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<Void> assignPassengers(
            @PathVariable Long id,
            @RequestBody PassengerAssignationRequest dto,
            Authentication authentication
    ) {
        passengerService.assignPassengersToSeats(id, dto, Long.valueOf(authentication.getName()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/split-setup")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<Void> setupSplitPayment(
            @PathVariable Long id,
            @RequestBody SplitPaymentSetupRequest dto,
            Authentication authentication
    ) {
        bookingService.setupSplitPayment(id, dto, Long.valueOf(authentication.getName()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<String> procesarPago(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long payerUserId = Long.valueOf(authentication.getName());
        String mensaje = bookingService.procesarPago(id, payerUserId);
        return ResponseEntity.accepted().body(mensaje);
    }

    @PostMapping("/internal/{id}/payment-confirmed")
    @PreAuthorize("hasRole('ROLE_SERVICE_PAYMENT')")
    public ResponseEntity<String> confirmarPagoValidado(
            @PathVariable Long id,
            @Valid @RequestBody PaymentConfirmationRequest request
    ) {
        String mensaje = bookingService.confirmarPagoValidado(
                id,
                request.getPagadorId(),
                request.getTokenPago()
        );
        return ResponseEntity.ok(mensaje);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<ReservationResponse> obtenerReserva(
            @PathVariable Long id,
            Authentication authentication) {
        ReservationResponse respuesta = bookingService.obtenerReserva(id, Long.valueOf(authentication.getName()));
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/internal/{id}")
    @PreAuthorize("hasRole('ROLE_SERVICE_PAYMENT')")
    public ResponseEntity<ReservationResponse> obtenerReservaInterna(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.obtenerReservaInterna(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<Void> cancelarReservaManualmente(
            @PathVariable Long id,
            Authentication authentication
    ) {
        bookingService.cancelarReservaManualmente(id, Long.valueOf(authentication.getName()));
        return ResponseEntity.ok().build();
    }
}