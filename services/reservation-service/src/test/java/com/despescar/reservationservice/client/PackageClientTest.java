package com.despescar.reservationservice.client;

import com.despescar.reservationservice.dto.packagecatalog.response.PackageLookupResponse;
import com.despescar.reservationservice.exception.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageClientTest {

    @Mock
    private RestTemplate restTemplate;

    private PackageClient packageClient;

    @BeforeEach
    void setUp() {
        packageClient = new PackageClient(restTemplate, "http://localhost:8086");
    }

    @Test
    void getPackageById_devuelvePaqueteCuandoExiste() {
        Long packageId = 99L;
        UUID hotelId = UUID.randomUUID();
        PackageLookupResponse response = new PackageLookupResponse();
        response.setId(packageId);
        response.setFlightNumber("AR123");
        response.setHotelId(hotelId);
        response.setBasePrice(BigDecimal.valueOf(100));
        response.setActive(true);

        when(restTemplate.exchange(
                eq("http://localhost:8086/api/packages/{id}"),
                eq(org.springframework.http.HttpMethod.GET),
                any(HttpEntity.class),
                eq(PackageLookupResponse.class),
                eq(packageId)
        )).thenReturn(ResponseEntity.ok(response));

        PackageLookupResponse result = packageClient.getPackageById(packageId, "Bearer token");

        assertEquals(packageId, result.getId());
        assertEquals("AR123", result.getFlightNumber());
        assertEquals(hotelId, result.getHotelId());
    }

    @Test
    void getPackageById_lanzaNotFoundCuandoNoExiste() {
        Long packageId = 99L;

        when(restTemplate.exchange(
                eq("http://localhost:8086/api/packages/{id}"),
                eq(org.springframework.http.HttpMethod.GET),
                any(HttpEntity.class),
                eq(PackageLookupResponse.class),
                eq(packageId)
        )).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "not-found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        ));

        BookingException ex = assertThrows(BookingException.class,
                () -> packageClient.getPackageById(packageId, "Bearer token"));

        assertEquals("PACKAGE_NO_ENCONTRADO", ex.getCodigo());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
