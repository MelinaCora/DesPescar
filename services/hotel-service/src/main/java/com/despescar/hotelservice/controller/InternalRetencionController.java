package com.despescar.hotelservice.controller;

import com.despescar.hotelservice.dto.internal.ConfirmarRetencionRequest;
import com.despescar.hotelservice.dto.internal.RetencionRequest;
import com.despescar.hotelservice.dto.internal.RetencionResponse;
import com.despescar.hotelservice.service.RetencionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API interna de retenciones: solo reservation-service, con el token interno (ver SecurityConfig). */
@RestController
@RequestMapping("/internal/retenciones")
public class InternalRetencionController {

    private final RetencionService service;

    public InternalRetencionController(RetencionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RetencionResponse> crear(@Valid @RequestBody RetencionRequest pedido) {
        return new ResponseEntity<>(service.crear(pedido), HttpStatus.CREATED);
    }

    @PostMapping("/{id}/confirmar")
    public RetencionResponse confirmar(@PathVariable UUID id, @Valid @RequestBody ConfirmarRetencionRequest pedido) {
        return service.confirmar(id, pedido.nombreTitular());
    }

    @PostMapping("/{id}/liberar")
    public ResponseEntity<Void> liberar(@PathVariable UUID id) {
        service.liberar(id);
        return ResponseEntity.noContent().build();
    }
}
