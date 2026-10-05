package com.despescar.hotelservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.hotelservice.config.SecurityConfig;
import com.despescar.hotelservice.exception.HotelNotFoundException;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import com.despescar.hotelservice.service.HotelAdminService;
import com.despescar.hotelservice.service.HotelCatalogoService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

@WebMvcTest(HotelController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=" + HotelControllerTest.SECRET)
class HotelControllerTest {

    static final String SECRET = "test-secret-key-for-hotel-controller-1234567890";
    private static final UUID ID = UUID.fromString("3f2b8c1e-0000-0000-0000-000000000001");

    private static final String HOTEL_VALIDO = """
            {"nombre":"Llao Llao","ciudad":"Bariloche","pais":"Argentina","direccion":"Av. Bustillo km 25",
             "estrellas":5,"descripcion":"Frente al lago","allInclusive":false,
             "imagenes":["https://img/1.jpg"],"servicios":["WIFI","SPA"],
             "politicaCancelacion":[{"horasAntes":72,"porcentajeReembolso":100},{"horasAntes":0,"porcentajeReembolso":0}],
             "habitaciones":[{"nombre":"Doble","descripcion":"Vista al lago","capacidad":2,
                              "precioPorNoche":250000,"cantidadUnidades":4,"imagenes":[]}]}
            """;

    // Sin habitaciones (@NotEmpty) y con una imagen http (solo se aceptan https)
    private static final String HOTEL_INVALIDO = """
            {"nombre":"Llao Llao","ciudad":"Bariloche","pais":"Argentina","direccion":"Av. Bustillo km 25",
             "estrellas":5,"allInclusive":false,"imagenes":["http://inseguro/1.jpg"],"servicios":[],
             "politicaCancelacion":[{"horasAntes":0,"porcentajeReembolso":0}],"habitaciones":[]}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private HotelCatalogoService catalogo;
    @MockitoBean
    private HotelAdminService admin;

    private String jwt(String role) {
        return "Bearer " + Jwts.builder()
                .subject("alguien@mail.com")
                .claim("role", role)
                .claim("userId", 7)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void lasLecturasSonPublicas() throws Exception {
        when(catalogo.buscar(any(), any(), any(), any())).thenReturn(List.of());
        when(catalogo.destinos()).thenReturn(List.of());

        mockMvc.perform(get("/hoteles")).andExpect(status().isOk());
        mockMvc.perform(get("/hoteles/destinos")).andExpect(status().isOk());
        mockMvc.perform(get("/hoteles/" + ID)).andExpect(status().isOk());
    }

    @Test
    void pasaLosParametrosDeBusqueda() throws Exception {
        when(catalogo.buscar(any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/hoteles").param("destino", "Córdoba")
                        .param("checkIn", "2026-11-10").param("checkOut", "2026-11-12").param("huespedes", "2"))
                .andExpect(status().isOk());

        verify(catalogo).buscar(eq("Córdoba"), eq(LocalDate.of(2026, 11, 10)), eq(LocalDate.of(2026, 11, 12)), eq(2));
    }

    @Test
    void erroresDeNegocioYParametrosResponden400() throws Exception {
        when(catalogo.buscar(any(), any(), any(), any())).thenThrow(new SolicitudInvalidaException("Indicá check-in y check-out."));

        mockMvc.perform(get("/hoteles").param("checkIn", "2026-11-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Indicá check-in y check-out."));
        mockMvc.perform(get("/hoteles").param("checkIn", "no-es-fecha")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/hoteles/no-es-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void hotelInexistenteResponde404() throws Exception {
        when(catalogo.detalle(eq(ID), any(), any(), any())).thenThrow(new HotelNotFoundException(ID));

        mockMvc.perform(get("/hoteles/" + ID)).andExpect(status().isNotFound());
    }

    @Test
    void elAltaPideRolDeAdministrador() throws Exception {
        mockMvc.perform(post("/hoteles").contentType(MediaType.APPLICATION_JSON).content(HOTEL_VALIDO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/hoteles").header("Authorization", jwt("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(HOTEL_VALIDO))
                .andExpect(status().isForbidden());
        verify(admin, never()).crear(any());

        mockMvc.perform(post("/hoteles").header("Authorization", jwt("HOTEL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(HOTEL_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    void elAltaValidaElCuerpo() throws Exception {
        mockMvc.perform(post("/hoteles").header("Authorization", jwt("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(HOTEL_INVALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.habitaciones").exists())
                .andExpect(jsonPath("$.campos['imagenes[0]']").exists());
        verify(admin, never()).crear(any());
    }

    @Test
    void unErrorInesperadoEnUnaLecturaPublicaResponde500() throws Exception {
        when(catalogo.buscar(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/hoteles"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Ocurrió un error inesperado."));
    }

    @Test
    void unJsonMalFormadoResponde400() throws Exception {
        mockMvc.perform(post("/hoteles")
                        .header("Authorization", jwt("HOTEL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El cuerpo del pedido no es válido."));
    }
}
