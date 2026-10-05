package com.despescar.reservationservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.reservationservice.config.SecurityConfig;
import com.despescar.reservationservice.service.BookingService;
import com.despescar.reservationservice.service.CarritoService;
import com.despescar.reservationservice.service.PassengerService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Los 401/403 de Spring Security se escriben con el ObjectMapper y la hora del Clock del servicio. */
@WebMvcTest({CarritoController.class, BookingController.class})
@Import({SecurityConfig.class, JwtService.class, SeguridadErroresTest.Reloj.class})
@TestPropertySource(properties = "jwt.secret=" + SeguridadErroresTest.SECRET)
class SeguridadErroresTest {

    static final String SECRET = "test-secret-key-for-reservation-seguridad-12345678";

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CarritoService carritoService;
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private PassengerService passengerService;

    @Test
    void elTimestampDelSinSesionSaleDelClock() throws Exception {
        mockMvc.perform(get("/api/bookings/carrito"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Necesitás iniciar sesión."))
                .andExpect(jsonPath("$.timestamp").value("2026-10-05T15:00:00"));
    }
}
