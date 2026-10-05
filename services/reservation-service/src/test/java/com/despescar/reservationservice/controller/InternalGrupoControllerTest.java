package com.despescar.reservationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.reservationservice.config.SecurityConfig;
import com.despescar.reservationservice.dto.grupo.PagoParteRequest;
import com.despescar.reservationservice.dto.grupo.ParteInternaResponse;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.service.PagoParteService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Rutas internas de las partes (contrato CB3): solo con el token interno de payment-service. */
@WebMvcTest(InternalGrupoController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=" + InternalGrupoControllerTest.SECRET,
        "reservation-service.sync-token=" + InternalGrupoControllerTest.TOKEN})
class InternalGrupoControllerTest {

    static final String SECRET = "test-secret-key-for-reservation-partes-1234567890";
    static final String TOKEN = "token-de-payment";
    private static final String INTERNO = "X-Internal-Service-Token";
    private static final String PARTE = "/api/bookings/internal/12/partes/2";
    private static final String CUERPO = "{\"pagadorId\":9,\"tokenPago\":\"MOCK-2\",\"monto\":353333.33}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PagoParteService pagoParteService;

    @Test
    void devuelveLaParteConSuDuenoMontoYEstados() throws Exception {
        when(pagoParteService.parte(12L, 2)).thenReturn(new ParteInternaResponse(12L, 2, 9L,
                new BigDecimal("353333.33"), "ARS", EstadoParte.TOMADA, EstadoGrupo.ABIERTO, 86100L));

        mockMvc.perform(get(PARTE).header(INTERNO, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(12))
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.usuarioId").value(9))
                .andExpect(jsonPath("$.monto").value(353333.33))
                .andExpect(jsonPath("$.moneda").value("ARS"))
                .andExpect(jsonPath("$.estadoParte").value("TOMADA"))
                .andExpect(jsonPath("$.estadoGrupo").value("ABIERTO"))
                .andExpect(jsonPath("$.segundosRestantes").value(86100));
    }

    @Test
    void unaParteLibreTieneUsuarioNull() throws Exception {
        when(pagoParteService.parte(12L, 3)).thenReturn(new ParteInternaResponse(12L, 3, null,
                new BigDecimal("353333.33"), "ARS", EstadoParte.LIBRE, EstadoGrupo.ABIERTO, 86100L));

        mockMvc.perform(get("/api/bookings/internal/12/partes/3").header(INTERNO, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").isEmpty())
                .andExpect(jsonPath("$.estadoParte").value("LIBRE"));
    }

    @Test
    void sinGrupoResponde404ParteNoEncontrada() throws Exception {
        when(pagoParteService.parte(12L, 2)).thenThrow(new BookingException(
                "PARTE_NO_ENCONTRADA", "La parte no existe.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get(PARTE).header(INTERNO, TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PARTE_NO_ENCONTRADA"));
    }

    @Test
    void pagoConfirmadoResponde200ConEstadoMotivoYMensaje() throws Exception {
        when(pagoParteService.confirmarPago(eq(12L), eq(2), any()))
                .thenReturn(ConfirmacionPagoResponse.partePagada("Parte pagada. Faltan 1 de 3."));

        mockMvc.perform(post(PARTE + "/pago-confirmado").header(INTERNO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PARTE_PAGADA"))
                .andExpect(jsonPath("$.motivo").isEmpty())
                .andExpect(jsonPath("$.mensaje").value("Parte pagada. Faltan 1 de 3."));

        ArgumentCaptor<PagoParteRequest> pedido = ArgumentCaptor.forClass(PagoParteRequest.class);
        verify(pagoParteService).confirmarPago(eq(12L), eq(2), pedido.capture());
        org.junit.jupiter.api.Assertions.assertEquals(9L, pedido.getValue().pagadorId());
        org.junit.jupiter.api.Assertions.assertEquals("MOCK-2", pedido.getValue().tokenPago());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("353333.33"), pedido.getValue().monto());
    }

    @Test
    void unRechazoViajaComo200Rechazada() throws Exception {
        when(pagoParteService.confirmarPago(eq(12L), eq(2), any())).thenReturn(ConfirmacionPagoResponse.rechazada(
                ConfirmacionPagoResponse.PARTE_NO_ES_DEL_PAGADOR, "La parte es de otra persona."));

        mockMvc.perform(post(PARTE + "/pago-confirmado").header(INTERNO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivo").value("PARTE_NO_ES_DEL_PAGADOR"));
    }

    @Test
    void sinMontoResponde400Validacion() throws Exception {
        mockMvc.perform(post(PARTE + "/pago-confirmado").header(INTERNO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pagadorId\":9,\"tokenPago\":\"MOCK-2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verify(pagoParteService, never()).confirmarPago(anyLong(), anyInt(), any());
    }

    @Test
    void siHotelServiceFallaAlConfirmarSePropagaEl5xx() throws Exception {
        when(pagoParteService.confirmarPago(eq(12L), eq(2), any())).thenThrow(new BookingException(
                "SERVICIO_HOTEL_NO_DISPONIBLE", "hotel-service no responde.", HttpStatus.SERVICE_UNAVAILABLE));

        mockMvc.perform(post(PARTE + "/pago-confirmado").header(INTERNO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_HOTEL_NO_DISPONIBLE"));
    }

    // ---------- seguridad ----------

    @Test
    void sinTokenInternoResponde401() throws Exception {
        mockMvc.perform(get(PARTE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PARTE + "/pago-confirmado").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        verify(pagoParteService, never()).parte(anyLong(), anyInt());
    }

    @Test
    void conTokenInternoEquivocadoResponde401() throws Exception {
        mockMvc.perform(get(PARTE).header(INTERNO, "otro")).andExpect(status().isUnauthorized());
    }

    @Test
    void unJwtDeClienteNoAlcanzaParaLasRutasInternas() throws Exception {
        mockMvc.perform(get(PARTE).header("Authorization", GrupoPagoControllerTest.jwt("USER", 9L)))
                .andExpect(status().isUnauthorized());
        verify(pagoParteService, never()).parte(anyLong(), anyInt());
    }

    @Test
    void elTokenInternoNoHabilitaOtrosMetodosSobreLaParte() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(PARTE)
                        .header(INTERNO, TOKEN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PARTE).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        verify(pagoParteService, never()).confirmarPago(anyLong(), anyInt(), any());
    }
}
