package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.passengers.request.PassengerAssignationRequest;
import com.despescar.reservationservice.dto.reservation.request.BookingInitRequest;
import com.despescar.reservationservice.dto.reservation.request.PaymentConfirmationRequest;
import com.despescar.reservationservice.dto.reservation.request.SplitPaymentSetupRequest;
import com.despescar.reservationservice.dto.reservation.response.BookingInitResponse;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.service.BookingService;
import com.despescar.reservationservice.service.PassengerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final PassengerService passengerService;

    @PostMapping("/init")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<BookingInitResponse> initBooking(
            @Valid @RequestBody BookingInitRequest dto,
            @RequestHeader(value = "Authorization", required = false) String token,
            Authentication authentication) {
        BookingInitResponse respuesta = bookingService.initializeBooking(dto, token, usuario(authentication));
        return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/passengers")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<Void> assignPassengers(
            @PathVariable Long id,
            @Valid @RequestBody PassengerAssignationRequest dto,
            Authentication authentication) {
        passengerService.assignPassengersToSeats(id, dto, usuario(authentication));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/split-setup")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<Void> setupSplitPayment(
            @PathVariable Long id,
            @RequestBody SplitPaymentSetupRequest dto,
            Authentication authentication) {
        bookingService.setupSplitPayment(id, dto, usuario(authentication));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<String> procesarPago(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.accepted().body(bookingService.procesarPago(id, usuario(authentication)));
    }

    @PostMapping("/internal/{id}/payment-confirmed")
    @PreAuthorize("hasRole('ROLE_SERVICE_PAYMENT')")
    public ResponseEntity<String> confirmarPagoValidado(
            @PathVariable Long id,
            @Valid @RequestBody PaymentConfirmationRequest request) {
        return ResponseEntity.ok(bookingService.confirmarPagoValidado(id, request.getPagadorId(), request.getTokenPago()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<ReservationResponse> obtenerReserva(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(bookingService.obtenerReserva(id, usuario(authentication)));
    }

    @GetMapping("/internal/{id}")
    @PreAuthorize("hasRole('ROLE_SERVICE_PAYMENT')")
    public ResponseEntity<ReservationResponse> obtenerReservaInterna(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.obtenerReservaInterna(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<Void> cancelarReservaManualmente(@PathVariable Long id, Authentication authentication) {
        bookingService.cancelarReservaManualmente(id, usuario(authentication));
        return ResponseEntity.ok().build();
    }

    private static Long usuario(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
