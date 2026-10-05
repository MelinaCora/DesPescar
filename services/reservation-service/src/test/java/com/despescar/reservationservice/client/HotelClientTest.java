package com.despescar.reservationservice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.dto.hotel.RetencionHotelRequest;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.exception.BookingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class HotelClientTest {

    private static final String BASE = "http://localhost:8083";
    private static final String CREAR = BASE + "/internal/retenciones";

    @Mock
    private RestTemplate restTemplate;

    private HotelClient hotelClient;

    @BeforeEach
    void setUp() {
        hotelClient = new HotelClient(restTemplate, BASE + "/", "token-interno");
    }

    private static RetencionHotelRequest pedido() {
        return new RetencionHotelRequest(12L, 7L, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 2, 3, Instant.parse("2026-10-05T18:15:00Z"));
    }

    private static HttpClientErrorException error(HttpStatus status, String cuerpo) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                cuerpo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private void crearFallaCon(Exception ex) {
        when(restTemplate.exchange(eq(CREAR), eq(HttpMethod.POST), any(HttpEntity.class), eq(RetencionHotelResponse.class)))
                .thenThrow(ex);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void creaLaRetencionConElTokenInternoYSinJwt() {
        RetencionHotelResponse esperada = new RetencionHotelResponse();
        esperada.setRetencionId(UUID.randomUUID());
        when(restTemplate.exchange(eq(CREAR), eq(HttpMethod.POST), any(HttpEntity.class), eq(RetencionHotelResponse.class)))
                .thenReturn(ResponseEntity.ok(esperada));

        RetencionHotelResponse r = hotelClient.crearRetencion(pedido());

        assertEquals(esperada.getRetencionId(), r.getRetencionId());
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(CREAR), eq(HttpMethod.POST), captor.capture(), eq(RetencionHotelResponse.class));
        assertEquals("token-interno", captor.getValue().getHeaders().getFirst("X-Internal-Service-Token"));
        assertNull(captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void confirmaConElNombreDelTitular() {
        UUID id = UUID.randomUUID();
        String url = BASE + "/internal/retenciones/" + id + "/confirmar";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(RetencionHotelResponse.class)))
                .thenReturn(ResponseEntity.ok(new RetencionHotelResponse()));

        hotelClient.confirmarRetencion(id, "Ana Pérez");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), captor.capture(), eq(RetencionHotelResponse.class));
        assertEquals("Ana Pérez", ((Map<String, String>) captor.getValue().getBody()).get("nombreTitular"));
    }

    @Test
    void liberarLlamaAlEndpointInterno() {
        UUID id = UUID.randomUUID();
        String url = BASE + "/internal/retenciones/" + id + "/liberar";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.noContent().build());

        hotelClient.liberarRetencion(id);

        verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void sinLugarSeTraduceA409() {
        crearFallaCon(error(HttpStatus.CONFLICT,
                "{\"error\":\"No quedan habitaciones de ese tipo para esas fechas.\",\"codigo\":\"SIN_DISPONIBILIDAD\"}"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.crearRetencion(pedido()));

        assertEquals("SIN_DISPONIBILIDAD_HOTEL", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("No quedan habitaciones de ese tipo para esas fechas.", ex.getMessage());
    }

    @Test
    void unaRetencionLiberadaConservaSuCodigo() {
        UUID id = UUID.randomUUID();
        when(restTemplate.exchange(eq(BASE + "/internal/retenciones/" + id + "/confirmar"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(RetencionHotelResponse.class)))
                .thenThrow(error(HttpStatus.CONFLICT, "{\"error\":\"La retención ya fue liberada.\",\"codigo\":\"RETENCION_LIBERADA\"}"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.confirmarRetencion(id, "Ana"));

        assertEquals("RETENCION_LIBERADA", ex.getCodigo());
    }

    @Test
    void datosInvalidosSeTraducenA400ConElMensajeDelHotel() {
        crearFallaCon(error(HttpStatus.BAD_REQUEST, "{\"error\":\"El check-out tiene que ser posterior al check-in.\"}"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.crearRetencion(pedido()));

        assertEquals("SOLICITUD_HOTEL_INVALIDA", ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("El check-out tiene que ser posterior al check-in.", ex.getMessage());
    }

    @Test
    void habitacionInexistenteSeTraduceA404() {
        crearFallaCon(error(HttpStatus.NOT_FOUND, "{\"error\":\"La habitación elegida no existe o no está disponible.\"}"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.crearRetencion(pedido()));

        assertEquals("HOTEL_NO_ENCONTRADO", ex.getCodigo());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void unTokenRechazadoEsUnErrorDeIntegracion() {
        crearFallaCon(error(HttpStatus.UNAUTHORIZED, ""));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.crearRetencion(pedido()));

        assertEquals("HOTEL_SERVICE_CLIENT_ERROR", ex.getCodigo());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void unaCaidaDelHotelSeTraduceA503() {
        crearFallaCon(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "error", HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8));
        BookingException caida = assertThrows(BookingException.class, () -> hotelClient.crearRetencion(pedido()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, caida.getStatus());
    }

    @Test
    void unTimeoutSeTraduceA504() {
        crearFallaCon(new ResourceAccessException("Read timed out"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.crearRetencion(pedido()));

        assertEquals("HOTEL_SERVICE_TIMEOUT", ex.getCodigo());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
    }
}
