package com.despescar.hotelservice.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.hotelservice.config.SecurityConfig;
import com.despescar.hotelservice.controller.HotelController;
import com.despescar.hotelservice.service.HotelService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Recorre la cadena de seguridad real: el ajuste de inventario solo lo puede hacer reservation-service. */
@WebMvcTest(HotelController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-inventory-security-1234567890",
        "inventory.sync-token=token-interno"
})
class InventoryEndpointSecurityTest {

    private static final String URL = "/hoteles/3f2b8c1e-0000-0000-0000-000000000001/rooms";
    private static final String SECRET = "test-secret-key-for-inventory-security-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotelService service;

    private String jwt(String role) {
        return Jwts.builder()
                .subject("alguien@mail.com")
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void sinCredencialesResponde401() throws Exception {
        mockMvc.perform(patch(URL).param("delta", "-1")).andExpect(status().isUnauthorized());
        verify(service, never()).adjustRooms(any(), anyInt());
    }

    @Test
    void conTokenDeUsuarioNoAlcanza() throws Exception {
        mockMvc.perform(patch(URL).param("delta", "-1").header("Authorization", "Bearer " + jwt("USER")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(URL).param("delta", "-1").header("Authorization", "Bearer " + jwt("SUPER_ADMIN")))
                .andExpect(status().isUnauthorized());
        verify(service, never()).adjustRooms(any(), anyInt());
    }

    @Test
    void conTokenInternoIncorrectoResponde401() throws Exception {
        mockMvc.perform(patch(URL).param("delta", "-1").header("X-Internal-Service-Token", "falso"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).adjustRooms(any(), anyInt());
    }

    @Test
    void conTokenInternoCorrectoSeAjustaElInventario() throws Exception {
        mockMvc.perform(patch(URL).param("delta", "-1").header("X-Internal-Service-Token", "token-interno"))
                .andExpect(status().isNoContent());
        verify(service).adjustRooms(any(), anyInt());
    }
}
