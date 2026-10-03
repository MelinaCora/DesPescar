package com.despescar.payment_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	/**
	 * Creates a new payment.
	 */
	@PostMapping
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<PaymentResponse> createPayment(
			@Valid @RequestBody PaymentRequest request,
			Authentication authentication) {

		PaymentResponse response =
				paymentService.createPayment(request, Long.valueOf(authentication.getName()));

		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(response);
	}

	/**
	 * Gets a payment by ID.
	 */
	@GetMapping("/{paymentId}")
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<PaymentResponse> getPaymentById(
			@PathVariable UUID paymentId,
			Authentication authentication) {

		PaymentResponse response =
				paymentService.getPaymentById(paymentId, Long.valueOf(authentication.getName()));

		return ResponseEntity.ok(response);
	}

	/**
	 * Gets all payments associated with a user.
	 */
	@GetMapping("/user/{userId}")
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<List<PaymentResponse>> getPaymentsByUser(
			@PathVariable Long userId,
			Authentication authentication) {

		List<PaymentResponse> response =
				paymentService.getPaymentsByUser(userId, Long.valueOf(authentication.getName()));

		return ResponseEntity.ok(response);
	}

	/**
	 * Gets all payments associated with a reservation.
	 */
	@GetMapping("/reservation/{reservationId}")
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<List<PaymentResponse>> getPaymentsByReservation(
			@PathVariable Long reservationId,
			Authentication authentication) {

		List<PaymentResponse> response =
				paymentService.getPaymentsByReservation(
						reservationId,
						Long.valueOf(authentication.getName())
				);

		return ResponseEntity.ok(response);
	}

	/**
	 * Cancels a pending payment.
	 */
	@DeleteMapping("/{paymentId}")
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<PaymentResponse> cancelPayment(
			@PathVariable UUID paymentId,
			Authentication authentication) {

		PaymentResponse response =
				paymentService.cancelPayment(paymentId, Long.valueOf(authentication.getName()));

		return ResponseEntity.ok(response);
	}
}
