package com.despescar.reservationservice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.despescar.reservationservice.config.HotelClientConfig;
import com.despescar.reservationservice.dto.hotel.RetencionHotelRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.exception.BookingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** Serializacion real: el RestTemplate es el mismo que arma HotelClientConfig. */
class HotelClientHttpTest {

    private static final String BASE = "http://localhost:8083";

    private MockRestServiceServer server;
    private HotelClient hotelClient;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new HotelClientConfig().hotelServiceRestTemplate(3000, 5000);
        server = MockRestServiceServer.bindTo(restTemplate).build();
        hotelClient = new HotelClient(restTemplate, BASE, "token-interno");
    }

    private static final String RESPUESTA = """
            {"retencionId":"8f14e45f-ceea-467a-9b3e-2a5b2c0d1e11","hotelId":"8f14e45f-ceea-467a-9b3e-2a5b2c0d1e12",
             "hotelNombre":"Sheraton Córdoba","ciudad":"Córdoba",
             "tipoHabitacionId":"8f14e45f-ceea-467a-9b3e-2a5b2c0d1e13","tipoHabitacionNombre":"Doble",
             "checkIn":"2026-11-10","checkOut":"2026-11-12","noches":2,"cantidad":2,"huespedes":3,
             "precioTotal":580000.00,"moneda":"ARS","horaCheckIn":"14:00:00",
             "zonaHoraria":"America/Argentina/Buenos_Aires",
             "politicaCancelacion":[{"horasAntes":48,"porcentajeReembolso":100},{"horasAntes":0,"porcentajeReembolso":0}],
             "estado":"RETENIDA","expiraEn":"2026-10-05T18:15:00Z","campoNuevo":"ignorado"}
            """;

    private static RetencionHotelRequest pedido() {
        return new RetencionHotelRequest(12L, 7L, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 2, 3, Instant.parse("2026-10-05T18:15:00Z"));
    }

    @Test
    void crearEnviaFechasIsoYTokenYParseaLaRespuesta() {
        server.expect(requestTo(BASE + "/internal/retenciones"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "token-interno"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.checkIn").value("2026-11-10"))
                .andExpect(jsonPath("$.checkOut").value("2026-11-12"))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-05T18:15:00Z"))
                .andExpect(jsonPath("$.cantidad").value(2))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(RESPUESTA));

        RetencionHotelResponse r = hotelClient.crearRetencion(pedido());

        server.verify();
        assertEquals(LocalTime.of(14, 0), r.getHoraCheckIn());
        assertEquals(0, new BigDecimal("580000.00").compareTo(r.getPrecioTotal()));
        assertEquals(LocalDate.of(2026, 11, 10), r.getCheckIn());
        assertEquals(Instant.parse("2026-10-05T18:15:00Z"), r.getExpiraEn());
        assertEquals(2, r.getPoliticaCancelacion().size());
        assertEquals(48, r.getPoliticaCancelacion().get(0).horasAntes());
        assertEquals(100, r.getPoliticaCancelacion().get(0).porcentajeReembolso());
        assertEquals("RETENIDA", r.getEstado());
    }

    @Test
    void confirmarDevuelve200ConElTitular() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/internal/retenciones/" + id + "/confirmar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "token-interno"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(jsonPath("$.nombreTitular").value("Ana Pérez"))
                .andRespond(withSuccess(RESPUESTA.replace("RETENIDA", "CONFIRMADA"), MediaType.APPLICATION_JSON));

        assertEquals("CONFIRMADA", hotelClient.confirmarRetencion(id, "Ana Pérez").getEstado());
        server.verify();
    }

    @Test
    void liberarAceptaElNoContent() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/internal/retenciones/" + id + "/liberar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "token-interno"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withNoContent());

        hotelClient.liberarRetencion(id);
        server.verify();
    }

    @Test
    void unaRetencionLiberadaConservaSuCodigoEnElCuerpoReal() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/internal/retenciones/" + id + "/confirmar"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"La retención ya fue liberada.\",\"codigo\":\"RETENCION_LIBERADA\"}"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.confirmarRetencion(id, "Ana"));

        assertEquals("RETENCION_LIBERADA", ex.getCodigo());
        assertEquals("La retención ya fue liberada.", ex.getMessage());
    }

    @Test
    void unNotFoundSinCuerpoUsaElMensajeGenerico() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/internal/retenciones/" + id + "/liberar"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.liberarRetencion(id));

        assertEquals("HOTEL_NO_ENCONTRADO", ex.getCodigo());
        assertEquals("No se encontró la habitación o la retención en el hotel.", ex.getMessage());
    }

    @Test
    void unTitularVacioNoLlegaAlHotel() {
        UUID id = UUID.randomUUID();

        BookingException nulo = assertThrows(BookingException.class, () -> hotelClient.confirmarRetencion(id, null));
        BookingException blanco = assertThrows(BookingException.class, () -> hotelClient.confirmarRetencion(id, "  "));

        assertEquals("SOLICITUD_HOTEL_INVALIDA", nulo.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, blanco.getStatus());
        server.verify();
    }

    @Test
    void unMensajeConEscapesSeLeeConElParserJson() {
        server.expect(requestTo(BASE + "/internal/retenciones"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Tipo \\\"Doble\\\" inv\\u00e1lido\"}"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.crearRetencion(pedido()));

        assertEquals("Tipo \"Doble\" inválido", ex.getMessage());
    }
}
