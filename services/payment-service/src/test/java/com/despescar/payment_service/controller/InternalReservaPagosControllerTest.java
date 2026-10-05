package com.despescar.payment_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.despescar.common.security.JwtService;
import com.despescar.payment_service.config.SecurityConfig;
import com.despescar.payment_service.dto.response.ReembolsoReservaResponse;
import com.despescar.payment_service.service.ReembolsoReservaService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Ruta interna del reembolso de una reserva cancelada: solo con el token de reservation-service. */
@WebMvcTest(InternalReservaPagosController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=" + InternalReservaPagosControllerTest.SECRET,
        "payment-service.sync-token=" + InternalReservaPagosControllerTest.TOKEN})
class InternalReservaPagosControllerTest {

    static final String SECRET = "test-secret-key-for-payment-reservas-123456789";
    static final String TOKEN = "token-de-reservas";
    private static final String INTERNO = "X-Internal-Service-Token";
    private static final String URL = "/api/payments/internal/reservas/15/reembolso";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReembolsoReservaService servicio;

    private static String jwtCliente() {
        return "Bearer " + Jwts.builder()
                .subject("usuario9@mail.com")
                .claim("role", "USER")
                .claim("userId", 9L)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }


    @Test
    void conElTokenInternoReembolsaYDevuelveLoReembolsado() throws Exception {
        when(servicio.reembolsar(15L, new BigDecimal("500.00"), "CANCELADA_POR_USUARIO"))
                .thenReturn(new ReembolsoReservaResponse(new BigDecimal("500.00"), 0));

        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":500.00,\"motivo\":\"CANCELADA_POR_USUARIO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reembolsado").value(500.00))
                .andExpect(jsonPath("$.fallidos").value(0));
    }

    @Test
    void sinMontoOConMontoCeroResponde400() throws Exception {
        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":0}"))
                .andExpect(status().isBadRequest());
        verify(servicio, never()).reembolsar(anyLong(), any(), any());
    }

    @Test
    void sinTokenOConJwtDeClienteResponde401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"monto\":10}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).header(INTERNO, "otro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":10}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).header("Authorization", jwtCliente()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":10}"))
                .andExpect(status().isUnauthorized());
        verify(servicio, never()).reembolsar(anyLong(), any(), any());
    }
}
