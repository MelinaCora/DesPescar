package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.carrito.AgregarEstadiaRequest;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.service.CarritoService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El carrito del usuario del token (contrato C4). 204 cuando no hay carrito o quedó vacío. */
@RestController
@RequestMapping("/api/bookings/carrito")
@RequiredArgsConstructor
public class CarritoController {

    private final CarritoService carritoService;

    @GetMapping
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<ReservationResponse> obtener(Authentication authentication) {
        return respuesta(carritoService.obtenerCarrito(usuario(authentication)));
    }

    @PostMapping("/estadias")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<ReservationResponse> agregarEstadia(
            @Valid @RequestBody AgregarEstadiaRequest pedido, Authentication authentication) {
        return new ResponseEntity<>(carritoService.agregarEstadia(pedido, usuario(authentication)), HttpStatus.CREATED);
    }

    @DeleteMapping("/estadias/{estadiaId}")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<ReservationResponse> quitarEstadia(@PathVariable Long estadiaId, Authentication authentication) {
        return respuesta(carritoService.quitarEstadia(estadiaId, usuario(authentication)));
    }

    @DeleteMapping("/vuelo")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<ReservationResponse> quitarVuelo(Authentication authentication) {
        return respuesta(carritoService.quitarVuelo(usuario(authentication)));
    }

    private static ResponseEntity<ReservationResponse> respuesta(Optional<ReservationResponse> carrito) {
        return carrito.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static Long usuario(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
