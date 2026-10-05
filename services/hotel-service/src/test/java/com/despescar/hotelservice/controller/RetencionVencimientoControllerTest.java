package com.despescar.hotelservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.hotelservice.config.SecurityConfig;
import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.dto.internal.RetencionResponse;
import com.despescar.hotelservice.entity.EstadoRetencion;
import com.despescar.hotelservice.exception.ConflictoException;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import com.despescar.hotelservice.service.RetencionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
        "jwt.secret=" + RetencionVencimientoControllerTest.SECRET,
        "inventory.sync-token=" + RetencionVencimientoControllerTest.TOKEN})
class RetencionVencimientoControllerTest {

    static final String SECRET = "test-secret-key-for-hotel-vencimiento-1234567890";
    static final String TOKEN = "token-interno-test";
    private static final String HEADER = "X-Internal-Service-Token";
    private static final UUID RET = UUID.fromString("3f2b8c1e-0000-0000-0000-0000000000aa");
    private static final Instant NUEVO = Instant.parse("2026-10-06T18:15:00Z");
    private static final String URL = "/internal/retenciones/" + RET + "/vencimiento";
    private static final String CUERPO = "{\"expiraEn\":\"2026-10-06T18:15:00Z\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RetencionService service;

    private static RetencionResponse respuesta() {
        return new RetencionResponse(RET, UUID.randomUUID(), "Sheraton Córdoba", "Córdoba", UUID.randomUUID(), "Doble",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 2, 2, 3, new BigDecimal("400000.00"),
                "ARS", LocalTime.of(14, 0), "America/Argentina/Buenos_Aires", List.of(new TramoDto(48, 100)),
                EstadoRetencion.RETENIDA, NUEVO);
    }

    @Test
    void conElTokenCambiaElVencimiento() throws Exception {
        when(service.cambiarVencimiento(RET, NUEVO)).thenReturn(respuesta());

        mockMvc.perform(post(URL).header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retencionId").value(RET.toString()))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-06T18:15:00Z"));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        verify(service, never()).cambiarVencimiento(any(), any());
    }

    @Test
    void sinExpiraEnResponde400() throws Exception {
        mockMvc.perform(post(URL).header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).cambiarVencimiento(any(), any());
    }

    @Test
    void unVencimientoFueraDeRangoResponde400ConMensaje() throws Exception {
        when(service.cambiarVencimiento(eq(RET), any()))
                .thenThrow(new SolicitudInvalidaException("Una retención puede vencer como máximo 25 horas después de ahora."));

        mockMvc.perform(post(URL).header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void sinLugarResponde409ConCodigo() throws Exception {
        when(service.cambiarVencimiento(eq(RET), any()))
                .thenThrow(new ConflictoException("SIN_DISPONIBILIDAD", "No quedan habitaciones de ese tipo para esas fechas."));

        mockMvc.perform(post(URL).header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_DISPONIBILIDAD"));
    }
}
