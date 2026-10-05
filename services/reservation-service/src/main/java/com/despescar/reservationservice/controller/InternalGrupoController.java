package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.grupo.PagoParteRequest;
import com.despescar.reservationservice.dto.grupo.ParteInternaResponse;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.service.PagoParteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Partes de un pago en grupo para payment-service (contrato CB3). Solo con el token interno
 * (SecurityConfig); el gateway no expone /internal (D22 de E2).
 */
@RestController
@RequestMapping("/api/bookings/internal/{id}/partes/{numero}")
@RequiredArgsConstructor
public class InternalGrupoController {

    private final PagoParteService pagoParteService;

    @GetMapping
    @PreAuthorize("hasRole('ROLE_SERVICE_PAYMENT')")
    public ResponseEntity<ParteInternaResponse> parte(@PathVariable Long id, @PathVariable int numero) {
        return ResponseEntity.ok(pagoParteService.parte(id, numero));
    }

    /** 200 con {estado, motivo, mensaje} de CB3; 400 VALIDACION; 5xx si hotel-service falla al confirmar. */
    @PostMapping("/pago-confirmado")
    @PreAuthorize("hasRole('ROLE_SERVICE_PAYMENT')")
    public ResponseEntity<ConfirmacionPagoResponse> pagoConfirmado(@PathVariable Long id, @PathVariable int numero,
                                                                   @Valid @RequestBody PagoParteRequest pedido) {
        return ResponseEntity.ok(pagoParteService.confirmarPago(id, numero, pedido));
    }
}
