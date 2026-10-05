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
import com.despescar.payment_service.dto.response.ReembolsoGrupoResponse;
import com.despescar.payment_service.service.ReembolsoGrupoService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Ruta interna de reembolsos de grupo (CB5): solo con el token de reservation-service. */
@WebMvcTest(InternalGrupoPagosController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=" + InternalGrupoPagosControllerTest.SECRET,
        "payment-service.sync-token=" + InternalGrupoPagosControllerTest.TOKEN})
class InternalGrupoPagosControllerTest {

    static final String SECRET = "test-secret-key-for-payment-grupos-1234567890";
    static final String TOKEN = "token-de-reservas";
    private static final String INTERNO = "X-Internal-Service-Token";
    private static final String URL = "/api/payments/internal/grupos/12/reembolsos";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReembolsoGrupoService reembolsoGrupoService;

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
    void conElTokenInternoReembolsaYDevuelveLosContadores() throws Exception {
        when(reembolsoGrupoService.reembolsarGrupo(12L, "PAGO_EN_GRUPO_VENCIDO"))
                .thenReturn(new ReembolsoGrupoResponse(2, 1, 0));

        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"PAGO_EN_GRUPO_VENCIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reembolsados").value(2))
                .andExpect(jsonPath("$.cancelados").value(1))
                .andExpect(jsonPath("$.fallidos").value(0));
    }

    @Test
    void sinMotivoLlegaNullAlServicio() throws Exception {
        when(reembolsoGrupoService.reembolsarGrupo(eq12(), isNull())).thenReturn(new ReembolsoGrupoResponse(0, 0, 0));

        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(reembolsoGrupoService).reembolsarGrupo(eq12(), isNull());
    }

    private static Long eq12() {
        return org.mockito.ArgumentMatchers.eq(12L);
    }

    @Test
    void unMotivoDemasiadoLargoResponde400() throws Exception {
        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"" + "X".repeat(61) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(reembolsoGrupoService, never()).reembolsarGrupo(anyLong(), any());
    }

    @Test
    void unJsonRotoResponde400ConElCuerpoDelServicio() throws Exception {
        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(URL));
    }

    @Test
    void sinTokenOConTokenEquivocadoResponde401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).header(INTERNO, "otro").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(reembolsoGrupoService, never()).reembolsarGrupo(anyLong(), any());
    }

    @Test
    void elTokenDeReservationServiceNoSirveParaLasRutasDeClientes() throws Exception {
        mockMvc.perform(get("/api/payments/reservation/12").header(INTERNO, TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unJwtDeClienteNoAlcanzaParaLaRutaInterna() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(reembolsoGrupoService, never()).reembolsarGrupo(anyLong(), any());
    }
}
