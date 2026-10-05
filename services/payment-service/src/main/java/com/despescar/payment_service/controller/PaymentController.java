package com.despescar.payment_service.controller;

import com.despescar.payment_service.dto.request.CreateGroupRequestDTO;
import com.despescar.payment_service.dto.request.PaymentAuthRequestDTO;
import com.despescar.payment_service.dto.response.PaymentGroupResponseDTO;
import com.despescar.payment_service.entity.PaymentGroup;
import com.despescar.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	/**
	 * 1. GET: React ho crida per dibuixar el "Cistell Compartit" i veure qui falta per pagar
	 */
	@GetMapping("/group/{reservationId}")
	public ResponseEntity<PaymentGroupResponseDTO> getPaymentGroup(@PathVariable Long reservationId) {
		try {
			return ResponseEntity.ok(paymentService.getGroupResponse(reservationId));
		} catch (RuntimeException e) {
			return ResponseEntity.notFound().build();
		}
	}

	/**
	 * 2. POST (Intern): El microservei de Reserves ho crida quan es confirma el cistell
	 */
	@PostMapping("/group")
	public ResponseEntity<?> createPaymentGroup(@RequestBody CreateGroupRequestDTO request) {
		try {
			// Recibe el ID de la reserva y la lista de IDs de los amigos desde React
			PaymentGroup group = paymentService.createPaymentGroup(
					request.getReservationId(),
					request.getUserIds()
			);

			return ResponseEntity.status(HttpStatus.CREATED)
					.body("Grupo de pago creado. Expira a las: " + group.getExpiresAt());
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	/**
	 * 3. POST: React ho crida quan un amic carrega la seva targeta i envia el Token
	 */
	@PostMapping("/authorize-fraction")
	public ResponseEntity<?> authorizeFraction(@RequestBody PaymentAuthRequestDTO request) {
		try {
			boolean groupCompleted = paymentService.authorizeFractionPayment(request);

			if (groupCompleted) {
				return ResponseEntity.ok().body("Pagament completat amb èxit. La teva reserva s'està processant.");
			} else {
				return ResponseEntity.ok().body("Pagament retingut amb èxit. Esperant a la resta del grup.");
			}

		} catch (RuntimeException ex) {
			log.error("Error en processar la fracció: {}", ex.getMessage());
			return ResponseEntity.badRequest().body("Error intern: " + ex.getMessage());
		}
	}
}