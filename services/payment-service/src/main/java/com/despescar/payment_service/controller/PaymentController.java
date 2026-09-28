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
	public ResponseEntity<PaymentResponse> createPayment(
			@Valid @RequestBody PaymentRequest request) {

		PaymentResponse response =
				paymentService.createPayment(request);

		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(response);
	}

	/**
	 * Gets a payment by ID.
	 */
	@GetMapping("/{paymentId}")
	public ResponseEntity<PaymentResponse> getPaymentById(
			@PathVariable UUID paymentId) {

		PaymentResponse response =
				paymentService.getPaymentById(paymentId);

		return ResponseEntity.ok(response);
	}

	/**
	 * Gets all payments associated with a user.
	 */
	@GetMapping("/user/{userId}")
	public ResponseEntity<List<PaymentResponse>> getPaymentsByUser(
			@PathVariable UUID userId) {

		List<PaymentResponse> response =
				paymentService.getPaymentsByUser(userId);

		return ResponseEntity.ok(response);
	}

	/**
	 * Gets all payments associated with a reservation.
	 */
	@GetMapping("/reservation/{reservationId}")
	public ResponseEntity<List<PaymentResponse>> getPaymentsByReservation(
			@PathVariable UUID reservationId) {

		List<PaymentResponse> response =
				paymentService.getPaymentsByReservation(
						reservationId
				);

		return ResponseEntity.ok(response);
	}

	/**
	 * Cancels a pending payment.
	 */
	@DeleteMapping("/{paymentId}")
	public ResponseEntity<PaymentResponse> cancelPayment(
			@PathVariable UUID paymentId) {

		PaymentResponse response =
				paymentService.cancelPayment(paymentId);

		return ResponseEntity.ok(response);
	}
}

