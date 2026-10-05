package com.despescar.hotelservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.hotelservice.config.SecurityConfig;
import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.dto.internal.RetencionResponse;
import com.despescar.hotelservice.entity.EstadoRetencion;
import com.despescar.hotelservice.exception.ConflictoException;
import com.despescar.hotelservice.exception.RetencionNoEncontradaException;
import com.despescar.hotelservice.service.RetencionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalRetencionController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=" + InternalRetencionControllerTest.SECRET,
        "inventory.sync-token=" + InternalRetencionControllerTest.TOKEN})
class InternalRetencionControllerTest {

    static final String SECRET = "test-secret-key-for-hotel-internal-api-1234567890";
    static final String TOKEN = "token-interno-test";
    private static final String HEADER = "X-Internal-Service-Token";
    private static final UUID RET = UUID.fromString("3f2b8c1e-0000-0000-0000-0000000000aa");
    private static final UUID HOTEL = UUID.fromString("3f2b8c1e-0000-0000-0000-000000000001");
    private static final UUID TIPO = UUID.fromString("3f2b8c1e-0000-0000-0000-000000000002");
    private static final String PEDIDO = """
            {"reservaId":12,"usuarioId":7,"hotelId":"3f2b8c1e-0000-0000-0000-000000000001",
             "tipoHabitacionId":"3f2b8c1e-0000-0000-0000-000000000002","checkIn":"2026-11-10",
             "checkOut":"2026-11-12","cantidad":2,"huespedes":3,"expiraEn":"2026-10-05T18:15:00Z"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RetencionService service;

    private static RetencionResponse respuesta(EstadoRetencion estado) {
        return new RetencionResponse(RET, HOTEL, "Sheraton Córdoba", "Córdoba", TIPO, "Doble",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 2, 2, 3, new BigDecimal("400000.00"),
                "ARS", LocalTime.of(14, 0), "America/Argentina/Buenos_Aires", List.of(new TramoDto(48, 100)),
                estado, Instant.parse("2026-10-05T18:15:00Z"));
    }

    private String jwtUsuario() {
        return "Bearer " + Jwts.builder()
                .subject("cliente@mail.com")
                .claim("role", "USER")
                .claim("userId", 7)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void sinTokenInternoResponde401() throws Exception {
        mockMvc.perform(post("/internal/retenciones").contentType(MediaType.APPLICATION_JSON).content(PEDIDO))
                .andExpect(status().isUnauthorized());
        verify(service, never()).crear(any());
    }

    @Test
    void unJwtDeUsuarioNoAlcanzaParaLaApiInterna() throws Exception {
        mockMvc.perform(post("/internal/retenciones").header("Authorization", jwtUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO))
                .andExpect(status().isUnauthorized());
        verify(service, never()).crear(any());
    }

    @Test
    void unTokenEquivocadoResponde401() throws Exception {
        mockMvc.perform(post("/internal/retenciones").header(HEADER, "otro")
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO))
                .andExpect(status().isUnauthorized());
        verify(service, never()).crear(any());
    }

    @Test
    void conElTokenCreaLaRetencion() throws Exception {
        when(service.crear(any())).thenReturn(respuesta(EstadoRetencion.RETENIDA));

        mockMvc.perform(post("/internal/retenciones").header(HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retencionId").value(RET.toString()))
                .andExpect(jsonPath("$.precioTotal").value(400000.0))
                .andExpect(jsonPath("$.moneda").value("ARS"))
                .andExpect(jsonPath("$.estado").value("RETENIDA"));
    }

    @Test
    void unCuerpoIncompletoResponde400() throws Exception {
        mockMvc.perform(post("/internal/retenciones").header(HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":12}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos inválidos"));
        verify(service, never()).crear(any());
    }

    @Test
    void sinLugarResponde409ConCodigo() throws Exception {
        when(service.crear(any())).thenThrow(new ConflictoException("SIN_DISPONIBILIDAD",
                "No quedan habitaciones de ese tipo para esas fechas."));

        mockMvc.perform(post("/internal/retenciones").header(HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_DISPONIBILIDAD"))
                .andExpect(jsonPath("$.error").value("No quedan habitaciones de ese tipo para esas fechas."));
    }

    @Test
    void confirmaYLibera() throws Exception {
        when(service.confirmar(RET, "Ana Pérez")).thenReturn(respuesta(EstadoRetencion.CONFIRMADA));

        mockMvc.perform(post("/internal/retenciones/" + RET + "/confirmar").header(HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombreTitular\":\"Ana Pérez\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mockMvc.perform(post("/internal/retenciones/" + RET + "/liberar").header(HEADER, TOKEN))
                .andExpect(status().isNoContent());
        verify(service).liberar(RET);
    }

    @Test
    void confirmarSinTitularResponde400() throws Exception {
        mockMvc.perform(post("/internal/retenciones/" + RET + "/confirmar").header(HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombreTitular\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elRestoDeLaApiResponde401ConCuerpo() throws Exception {
        mockMvc.perform(post("/hoteles").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Necesitás iniciar sesión."));
    }

    @Test
    void unUsuarioSinRolDeAdminRecibe403ConCuerpo() throws Exception {
        mockMvc.perform(post("/hoteles").header("Authorization", jwtUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("No tenés permisos para esta acción."));
    }

    @Test
    void elCatalogoPublicoNoPideToken() throws Exception {
        // HotelController no se carga en este slice: un 404 prueba que la seguridad dejó pasar
        mockMvc.perform(get("/hoteles/destinos"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401 || s == 403) {
                        throw new AssertionError("El catálogo debería ser público, respondió " + s);
                    }
                });
    }

    @Test
    void confirmarUnaRetencionInexistenteResponde404() throws Exception {
        when(service.confirmar(RET, "Ana Pérez")).thenThrow(new RetencionNoEncontradaException(RET));

        mockMvc.perform(post("/internal/retenciones/" + RET + "/confirmar").header(HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombreTitular\":\"Ana Pérez\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmarUnaRetencionLiberadaResponde409() throws Exception {
        when(service.confirmar(RET, "Ana Pérez")).thenThrow(new ConflictoException("RETENCION_LIBERADA",
                "La retención ya fue liberada."));

        mockMvc.perform(post("/internal/retenciones/" + RET + "/confirmar").header(HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombreTitular\":\"Ana Pérez\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RETENCION_LIBERADA"));
    }

    @Test
    void elTokenInternoValidoLlegaAlControladorAunqueVengaUnJwtDeUsuario() throws Exception {
        when(service.crear(any())).thenReturn(respuesta(EstadoRetencion.RETENIDA));

        mockMvc.perform(post("/internal/retenciones").header(HEADER, TOKEN)
                        .header("Authorization", jwtUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO))
                .andExpect(status().isCreated());
    }
}
