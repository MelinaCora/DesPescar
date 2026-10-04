package com.despescar.payment_service.controller;

import com.despescar.payment_service.dto.request.CreateGroupRequestDTO;
import com.despescar.payment_service.dto.request.PaymentAuthRequestDTO;
import com.despescar.payment_service.dto.response.PaymentGroupResponseDTO;
import com.despescar.payment_service.entity.PaymentGroup;
import com.despescar.payment_service.service.PaymentService;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
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
	 * 1. GET: React lo llama para dibujar el "Carrito Compartido" y ver quién falta pagar
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
	 * 2. POST (Interno): El microservicio de Reservas lo llama cuando se confirma el carrito
	 */
	@PostMapping("/group")
	public ResponseEntity<?> createPaymentGroup(@RequestBody CreateGroupRequestDTO request) {
		try {
			PaymentGroup group = paymentService.createPaymentGroup(
					request.getReservationId(),
					request.getTotalAmount(),
					request.getUserIds()
			);
			return ResponseEntity.status(HttpStatus.CREATED).body("Grupo de pago creado. Expira a las: " + group.getExpiresAt());
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	/**
	 * 3. POST: React lo llama cuando un amigo carga su tarjeta y envía el Token
	 */
	@PostMapping("/authorize-fraction")
	public ResponseEntity<?> authorizeFraction(@RequestBody PaymentAuthRequestDTO request) {
		try {
			paymentService.authorizeFractionPayment(request);
			return ResponseEntity.ok().body("Pago retenido con éxito. Esperando al resto del grupo.");

		} catch (MPApiException apiEx) {
			log.error("Error de la API de MP: {}", apiEx.getApiResponse().getContent());
			return ResponseEntity.badRequest().body("Error al procesar la tarjeta con el banco.");

		} catch (MPException mpEx) {
			log.error("Error interno del SDK de MP: {}", mpEx.getMessage());
			return ResponseEntity.internalServerError().body("Error de comunicación con la pasarela.");

		}
    }
}