package com.despescar.reservationservice.controller;

import com.despescar.reservationservice.dto.grupo.EditarPartesRequest;
import com.despescar.reservationservice.dto.grupo.GrupoResponse;
import com.despescar.reservationservice.dto.grupo.GrupoResumenResponse;
import com.despescar.reservationservice.dto.grupo.IniciarGrupoRequest;
import com.despescar.reservationservice.dto.grupo.TokenGrupoRequest;
import com.despescar.reservationservice.dto.grupo.UnirseGrupoRequest;
import com.despescar.reservationservice.service.GrupoPagoService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pago en grupo (contrato CB2). Los códigos de error salen de GrupoPagoService; el token del enlace
 * llega siempre en el cuerpo (D-b6) y nunca se escribe en el log.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class GrupoPagoController {

    private final GrupoPagoService grupoService;

    // ---------- organizador ----------

    @PostMapping("/{id}/grupo")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<GrupoResponse> iniciar(@PathVariable Long id,
                                                 @Valid @RequestBody IniciarGrupoRequest pedido,
                                                 Authentication authentication) {
        GrupoResponse grupo = grupoService.iniciar(id, pedido.cantidadPartes(), usuario(authentication));
        return new ResponseEntity<>(grupo, HttpStatus.CREATED);
    }

    @GetMapping("/{id}/grupo")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<GrupoResponse> ver(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(grupoService.verComoOrganizador(id, usuario(authentication)));
    }

    /** `{cantidadPartes}` vuelve a partes iguales; `{montos}` carga montos a mano (uno de los dos). */
    @PutMapping("/{id}/grupo/partes")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<GrupoResponse> editarPartes(@PathVariable Long id,
                                                      @Valid @RequestBody EditarPartesRequest pedido,
                                                      Authentication authentication) {
        return ResponseEntity.ok(grupoService.editarPartes(id, pedido, usuario(authentication)));
    }

    @PostMapping("/{id}/grupo/partes/{numero}/liberar")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<GrupoResponse> liberarParte(@PathVariable Long id, @PathVariable int numero,
                                                      Authentication authentication) {
        return ResponseEntity.ok(grupoService.liberarParte(id, numero, usuario(authentication)));
    }

    @DeleteMapping("/{id}/grupo")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<GrupoResponse> cancelar(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(grupoService.cancelar(id, usuario(authentication)));
    }

    // ---------- integrantes y enlace ----------

    @GetMapping("/{id}/grupo/participacion")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<GrupoResponse> participacion(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(grupoService.participacion(id, usuario(authentication)));
    }

    @PostMapping("/grupos/consultar")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<GrupoResponse> consultar(@Valid @RequestBody TokenGrupoRequest pedido,
                                                   Authentication authentication) {
        return ResponseEntity.ok(grupoService.consultar(pedido.token(), usuario(authentication)));
    }

    @PostMapping("/grupos/unirse")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<GrupoResponse> unirse(@Valid @RequestBody UnirseGrupoRequest pedido,
                                                Authentication authentication) {
        return ResponseEntity.ok(grupoService.unirse(pedido.token(), pedido.apodo(), usuario(authentication)));
    }

    @GetMapping("/grupos/mios")
    @PreAuthorize("hasRole('ROLE_CLIENTE')")
    public ResponseEntity<List<GrupoResumenResponse>> misGrupos(Authentication authentication) {
        return ResponseEntity.ok(grupoService.misGrupos(usuario(authentication)));
    }

    private static Long usuario(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
