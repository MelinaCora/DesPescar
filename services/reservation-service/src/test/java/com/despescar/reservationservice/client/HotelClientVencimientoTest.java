package com.despescar.reservationservice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.despescar.reservationservice.config.HotelClientConfig;
import com.despescar.reservationservice.exception.BookingException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HotelClientVencimientoTest {

    private static final String BASE = "http://localhost:8083";
    private static final Instant NUEVO = Instant.parse("2026-10-06T18:15:00Z");
    private static final String RESPUESTA = """
            {"retencionId":"8f14e45f-ceea-467a-9b3e-2a5b2c0d1e11","hotelId":"8f14e45f-ceea-467a-9b3e-2a5b2c0d1e12",
             "hotelNombre":"Sheraton Córdoba","ciudad":"Córdoba",
             "tipoHabitacionId":"8f14e45f-ceea-467a-9b3e-2a5b2c0d1e13","tipoHabitacionNombre":"Doble",
             "checkIn":"2026-11-10","checkOut":"2026-11-12","noches":2,"cantidad":2,"huespedes":3,
             "precioTotal":580000.00,"moneda":"ARS","horaCheckIn":"14:00:00",
             "zonaHoraria":"America/Argentina/Buenos_Aires","politicaCancelacion":[],
             "estado":"RETENIDA","expiraEn":"2026-10-06T18:15:00Z"}
            """;

    private MockRestServiceServer server;
    private HotelClient hotelClient;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new HotelClientConfig().hotelServiceRestTemplate(3000, 5000);
        server = MockRestServiceServer.bindTo(restTemplate).build();
        hotelClient = new HotelClient(restTemplate, BASE, "token-interno");
    }

    @Test
    void mandaElVencimientoIsoConElTokenInterno() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/internal/retenciones/" + id + "/vencimiento"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "token-interno"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-06T18:15:00Z"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        assertEquals(NUEVO, hotelClient.cambiarVencimiento(id, NUEVO).getExpiraEn());
        server.verify();
    }

    @Test
    void sinLugarSeTraduceComoLasDemasRetenciones() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/internal/retenciones/" + id + "/vencimiento"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"No quedan habitaciones de ese tipo para esas fechas.\",\"codigo\":\"SIN_DISPONIBILIDAD\"}"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.cambiarVencimiento(id, NUEVO));

        assertEquals("SIN_DISPONIBILIDAD_HOTEL", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void unVencimientoInvalidoEsUnaSolicitudInvalida() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/internal/retenciones/" + id + "/vencimiento"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"El vencimiento nuevo ya pasó.\"}"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.cambiarVencimiento(id, NUEVO));

        assertEquals("SOLICITUD_HOTEL_INVALIDA", ex.getCodigo());
    }
}
