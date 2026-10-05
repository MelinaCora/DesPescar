package com.despescar.flightservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.flightservice.config.SecurityConfig;
import com.despescar.flightservice.service.FlightService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Fechas con vuelos de una ruta: lectura pública como la búsqueda, con la cadena de seguridad real. */
@WebMvcTest(FlightController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-inventory-security-1234567890",
        "inventory.sync-token=token-interno"
})
class FlightFechasEndpointTest {

    private static final String URL = "/api/flights/fechas";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlightService service;

    @Test
    void sinSesionDevuelveLasFechasComoTextoIso() throws Exception {
        when(service.fechasConVuelos("AEP", "COR", LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 22)));

        mockMvc.perform(get(URL).param("origin", "AEP").param("destination", "COR")
                        .param("desde", "2026-10-06").param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"2026-10-19\",\"2026-10-22\"]", true));
    }

    @Test
    void parametrosFaltantesOInvalidosResponden400SinConsultar() throws Exception {
        mockMvc.perform(get(URL).param("destination", "COR").param("desde", "2026-10-06")
                .param("hasta", "2026-10-31")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("origin", " ").param("destination", "COR").param("desde", "2026-10-06")
                .param("hasta", "2026-10-31")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("origin", "AEP").param("destination", "COR").param("desde", "6/10/2026")
                .param("hasta", "2026-10-31")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("origin", "AEP").param("destination", "COR").param("desde", "2026-10-06"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("origin", "AEP").param("destination", "COR").param("desde", "2026-10-06")
                .param("hasta", "2026-10-05")).andExpect(status().isBadRequest());
        verify(service, never()).fechasConVuelos(any(), any(), any(), any());
    }
}
