package com.despescar.payment_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.despescar.payment_service.dto.request.ReembolsoReservaRequest;
import com.despescar.payment_service.dto.response.ReembolsoReservaResponse;
import com.despescar.payment_service.service.ReembolsoReservaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Reembolso de una reserva cancelada por el usuario, a pedido de reservation-service. Solo con el
 * token interno PAYMENT_SERVICE_SYNC_TOKEN; el gateway cierra cualquier segmento /internal.
 */
@RestController
@RequestMapping("/api/payments/internal/reservas")
@RequiredArgsConstructor
public class InternalReservaPagosController {

    private final ReembolsoReservaService reembolsoReservaService;

    @PostMapping("/{reservaId}/reembolso")
    @PreAuthorize("hasRole('ROLE_SERVICE_RESERVATION')")
    public ResponseEntity<ReembolsoReservaResponse> reembolsar(@PathVariable Long reservaId,
                                                               @Valid @RequestBody ReembolsoReservaRequest pedido) {
        return ResponseEntity.ok(reembolsoReservaService.reembolsar(reservaId, pedido.monto(), pedido.motivo()));
    }
}
