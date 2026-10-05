package com.despescar.hotelservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.hotelservice.config.SecurityConfig;
import com.despescar.hotelservice.service.HotelAdminService;
import com.despescar.hotelservice.service.HotelCatalogoService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

/** Los errores propios de Spring MVC responden con su código y no con 500 (pendiente de E1). */
@WebMvcTest(HotelController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=" + ErroresHttpTest.SECRET)
class ErroresHttpTest {

    static final String SECRET = "test-secret-key-for-hotel-errores-http-1234567890";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private HotelCatalogoService catalogo;
    @MockitoBean
    private HotelAdminService admin;

    private String jwtAdmin() {
        return "Bearer " + Jwts.builder()
                .subject("admin@mail.com")
                .claim("role", "SUPER_ADMIN")
                .claim("userId", 1)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void rutaInexistenteResponde404ConCuerpo() throws Exception {
        mockMvc.perform(get("/hoteles/a/b").header("Authorization", jwtAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("El recurso no existe."));
    }

    @Test
    void metodoNoSoportadoResponde405() throws Exception {
        mockMvc.perform(delete("/hoteles").header("Authorization", jwtAdmin()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.error").value("Método no permitido para este recurso."));
    }

    @Test
    void tipoDeContenidoNoSoportadoResponde415() throws Exception {
        mockMvc.perform(post("/hoteles").header("Authorization", jwtAdmin())
                        .contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Tipo de contenido no soportado."));
    }
}
