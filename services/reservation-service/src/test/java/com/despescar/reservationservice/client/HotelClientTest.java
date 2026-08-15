package com.despescar.reservationservice.client;

import com.despescar.reservationservice.dto.hotel.response.HotelLookupResponse;
import com.despescar.reservationservice.exception.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelClientTest {

    @Mock
    private RestTemplate restTemplate;

    private HotelClient hotelClient;

    @BeforeEach
    void setUp() {
        hotelClient = new HotelClient(restTemplate, "http://localhost:8083");
    }

    @Test
    void getHotelById_devuelveHotelCuandoExiste() {
        UUID hotelId = UUID.randomUUID();
        HotelLookupResponse response = new HotelLookupResponse();
        response.setId(hotelId);

        when(restTemplate.getForObject(
                eq("http://localhost:8083/hoteles/{id}"),
                eq(HotelLookupResponse.class),
                eq(hotelId)
        )).thenReturn(response);

        HotelLookupResponse result = hotelClient.getHotelById(hotelId);

        assertEquals(hotelId, result.getId());
    }

    @Test
    void getHotelById_lanzaNotFoundCuandoNoExiste() {
        UUID hotelId = UUID.randomUUID();

        when(restTemplate.getForObject(
                eq("http://localhost:8083/hoteles/{id}"),
                eq(HotelLookupResponse.class),
                eq(hotelId)
        )).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "not-found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        ));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.getHotelById(hotelId));

        assertEquals("HOTEL_NO_ENCONTRADO", ex.getCodigo());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void getHotelById_lanzaBadGatewayCuandoRespuestaEsVacia() {
        UUID hotelId = UUID.randomUUID();

        when(restTemplate.getForObject(
                eq("http://localhost:8083/hoteles/{id}"),
                eq(HotelLookupResponse.class),
                eq(hotelId)
        )).thenReturn(null);

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.getHotelById(hotelId));

        assertEquals("HOTEL_SERVICE_EMPTY_RESPONSE", ex.getCodigo());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void getHotelById_lanzaGatewayTimeoutEnTimeout() {
        UUID hotelId = UUID.randomUUID();

        when(restTemplate.getForObject(
                eq("http://localhost:8083/hoteles/{id}"),
                eq(HotelLookupResponse.class),
                eq(hotelId)
        )).thenThrow(new ResourceAccessException("Read timed out"));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.getHotelById(hotelId));

        assertEquals("HOTEL_SERVICE_TIMEOUT", ex.getCodigo());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
    }

    @Test
    void getHotelById_lanzaServiceUnavailableEnError5xx() {
        UUID hotelId = UUID.randomUUID();

        when(restTemplate.getForObject(
                eq("http://localhost:8083/hoteles/{id}"),
                eq(HotelLookupResponse.class),
                eq(hotelId)
        )).thenThrow(HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "server-error",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        ));

        BookingException ex = assertThrows(BookingException.class, () -> hotelClient.getHotelById(hotelId));

        assertEquals("HOTEL_SERVICE_SERVER_ERROR", ex.getCodigo());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }
}


