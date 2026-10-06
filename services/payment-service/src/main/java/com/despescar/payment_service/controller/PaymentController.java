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

import com.despescar.payment_service.dto.request.ConciliacionPagoRequest;
import com.despescar.payment_service.dto.request.OrdenPagoRequest;
import com.despescar.payment_service.dto.request.PaymentRequest;
import com.despescar.payment_service.dto.request.SimulacionPagoRequest;
import com.despescar.payment_service.dto.response.OrdenPagoResponse;
import com.despescar.payment_service.dto.response.PaymentConfigResponse;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.service.MercadoPagoOrdenService;
import com.despescar.payment_service.service.PaymentConciliationService;
import com.despescar.payment_service.service.PaymentService;
import com.despescar.payment_service.service.PaymentSimulationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;
	private final PaymentSimulationService paymentSimulationService;
	private final PaymentConciliationService paymentConciliationService;
	private final MercadoPagoOrdenService mercadoPagoOrdenService;

	/** Proveedor activo y, con Mercado Pago (Orders), la public key con la que el front carga MercadoPago.js. */
	@GetMapping("/config")
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<PaymentConfigResponse> configuracion() {
		return ResponseEntity.ok(mercadoPagoOrdenService.configuracion());
	}

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

	/**
	 * Aprueba o rechaza un pago del proveedor mock (pagina /pago/simulado). 404 si el proveedor
	 * activo no es mock.
	 */
	@PostMapping("/{paymentId}/simulacion")
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<PaymentResponse> simularPago(
			@PathVariable UUID paymentId,
			@Valid @RequestBody SimulacionPagoRequest request,
			Authentication authentication) {

		return ResponseEntity.ok(paymentSimulationService.simular(
				paymentId, request.aprobado(), Long.valueOf(authentication.getName())));
	}

	/**
	 * Concilia un pago con Mercado Pago a partir del payment_id de la back_url (pagina
	 * /pago/resultado). 404 si el proveedor activo no es mercadopago.
	 */
	@PostMapping("/{paymentId}/conciliacion")
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<PaymentResponse> conciliarPago(
			@PathVariable UUID paymentId,
			@Valid @RequestBody ConciliacionPagoRequest request,
			Authentication authentication) {

		return ResponseEntity.ok(paymentConciliationService.conciliar(
				paymentId, request.mpPaymentId(), Long.valueOf(authentication.getName())));
	}

	/**
	 * Cobra un pago PENDING con el token de tarjeta de MercadoPago.js creando una orden en Mercado
	 * Pago (pagina /pago/mercadopago). 404 si el proveedor activo no es mercadopago_orders.
	 */
	@PostMapping("/{paymentId}/orden")
	@PreAuthorize("hasRole('ROLE_CLIENTE')")
	public ResponseEntity<OrdenPagoResponse> cobrarConOrden(
			@PathVariable UUID paymentId,
			@Valid @RequestBody OrdenPagoRequest request,
			Authentication authentication) {

		return ResponseEntity.ok(mercadoPagoOrdenService.cobrar(
				paymentId, request, Long.valueOf(authentication.getName())));
	}
}
