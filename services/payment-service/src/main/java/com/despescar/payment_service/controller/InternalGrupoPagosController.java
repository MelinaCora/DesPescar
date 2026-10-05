package com.despescar.payment_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.despescar.payment_service.dto.request.ReembolsoGrupoRequest;
import com.despescar.payment_service.dto.response.ReembolsoGrupoResponse;
import com.despescar.payment_service.service.ReembolsoGrupoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Reembolsos de un pago en grupo a pedido de reservation-service (contrato CB5). Solo con el token
 * interno PAYMENT_SERVICE_SYNC_TOKEN; el gateway cierra cualquier segmento /internal (D22 de E2).
 */
@RestController
@RequestMapping("/api/payments/internal/grupos")
@RequiredArgsConstructor
public class InternalGrupoPagosController {

    private final ReembolsoGrupoService reembolsoGrupoService;

    @PostMapping("/{reservaId}/reembolsos")
    @PreAuthorize("hasRole('ROLE_SERVICE_RESERVATION')")
    public ResponseEntity<ReembolsoGrupoResponse> reembolsar(@PathVariable Long reservaId,
                                                             @Valid @RequestBody(required = false) ReembolsoGrupoRequest pedido) {
        String motivo = pedido == null ? null : pedido.motivo();
        return ResponseEntity.ok(reembolsoGrupoService.reembolsarGrupo(reservaId, motivo));
    }
}
