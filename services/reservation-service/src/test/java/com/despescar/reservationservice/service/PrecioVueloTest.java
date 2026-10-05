package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.dto.flight.response.FareLookupResponse;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.exception.BookingException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PrecioVueloTest {

    private static final UUID IDA = UUID.randomUUID();
    private static final UUID VUELTA = UUID.randomUUID();
    private static final LocalDateTime SALIDA = LocalDateTime.of(2026, 10, 19, 8, 0);

    @Mock
    private FlightClient flightClient;

    private PrecioVuelo precioVuelo;

    @BeforeEach
    void setUp() {
        precioVuelo = new PrecioVuelo(flightClient);
    }

    static FareLookupResponse tarifa(UUID id, String nombre, String moneda, String valor) {
        FareLookupResponse.PriceDto precio = new FareLookupResponse.PriceDto();
        precio.setCurrency(moneda);
        precio.setTransparentFinalPrice(new BigDecimal(valor));
        FareLookupResponse t = new FareLookupResponse();
        t.setId(id);
        t.setName(nombre);
        t.setPrice(precio);
        return t;
    }

    private FlightLookupResponse vuelo(UUID id, String precio, String estado, int asientos, LocalDateTime salida,
                                       FareLookupResponse... tarifas) {
        FlightLookupResponse v = new FlightLookupResponse();
        v.setId(id);
        v.setFlightNumber("AR" + id.toString().substring(0, 4));
        v.setPrice(new BigDecimal(precio));
        v.setStatus(estado);
        v.setAvailableSeats(asientos);
        v.setDepartureTime(salida);
        v.setFares(new ArrayList<>(List.of(tarifas)));
        when(flightClient.getFlightByNumber(id)).thenReturn(v);
        return v;
    }

    @Test
    void idaYVueltaSumaElVueloYLaTarifaDeCadaTramo() {
        UUID light = UUID.randomUUID();
        UUID standard = UUID.randomUUID();
        vuelo(IDA, "160000", "SCHEDULED", 150, SALIDA, tarifa(light, "Light", "ARS", "0"));
        vuelo(VUELTA, "150000", "SCHEDULED", 150, SALIDA.plusDays(7), tarifa(standard, "Standard", "ARS", "45000"));

        PrecioVuelo.Cotizacion c = precioVuelo.cotizar(List.of(IDA, VUELTA), List.of(light, standard), 2);

        assertEquals(new BigDecimal("355000.00"), c.precioPorPasajero());
        assertEquals("Light / Standard", c.tarifas());
        assertEquals(SALIDA, c.salida());
    }

    @Test
    void laMismaTarifaEnLosDosTramosSeNombraUnaVez() {
        UUID ida = UUID.randomUUID();
        UUID vuelta = UUID.randomUUID();
        vuelo(IDA, "100000", "SCHEDULED", 10, SALIDA, tarifa(ida, "Light", "ARS", "0"));
        vuelo(VUELTA, "100000", "DELAYED", 10, SALIDA.plusDays(3), tarifa(vuelta, "Light", "ars", "0"));

        assertEquals("Light", precioVuelo.cotizar(List.of(IDA, VUELTA), List.of(ida, vuelta), 1).tarifas());
    }

    @Test
    void unaTarifaQueNoEsDelVueloSeRechaza() {
        vuelo(IDA, "160000", "SCHEDULED", 150, SALIDA, tarifa(UUID.randomUUID(), "Light", "ARS", "0"));

        BookingException ex = assertThrows(BookingException.class,
                () -> precioVuelo.cotizar(List.of(IDA), List.of(UUID.randomUUID()), 1));

        assertEquals("TARIFA_INVALIDA", ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void hayQueElegirUnaTarifaPorTramo() {
        BookingException ex = assertThrows(BookingException.class,
                () -> precioVuelo.cotizar(List.of(IDA, VUELTA), List.of(UUID.randomUUID()), 1));

        assertEquals("TARIFAS_INVALIDAS", ex.getCodigo());
        verifyNoInteractions(flightClient);
    }

    @Test
    void unaTarifaEnDolaresNoEntraAlCarrito() {
        UUID usd = UUID.randomUUID();
        vuelo(IDA, "160", "SCHEDULED", 150, SALIDA, tarifa(usd, "Light", "USD", "0"));

        BookingException ex = assertThrows(BookingException.class, () -> precioVuelo.cotizar(List.of(IDA), List.of(usd), 1));

        assertEquals("MONEDA_NO_SOPORTADA", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void unVueloCanceladoNoSeReserva() {
        UUID t = UUID.randomUUID();
        vuelo(IDA, "160000", "CANCELLED", 150, SALIDA, tarifa(t, "Light", "ARS", "0"));

        BookingException ex = assertThrows(BookingException.class, () -> precioVuelo.cotizar(List.of(IDA), List.of(t), 1));

        assertEquals("VUELO_NO_RESERVABLE", ex.getCodigo());
    }

    @Test
    void sinAsientosSuficientesResponde409() {
        UUID t = UUID.randomUUID();
        vuelo(IDA, "160000", "SCHEDULED", 1, SALIDA, tarifa(t, "Light", "ARS", "0"));

        BookingException ex = assertThrows(BookingException.class, () -> precioVuelo.cotizar(List.of(IDA), List.of(t), 2));

        assertEquals("SIN_DISPONIBILIDAD", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void redondeaElPrecioDelVueloYDeCadaTarifaAntesDeSumar() {
        UUID t = UUID.randomUUID();
        vuelo(IDA, "85000.005", "SCHEDULED", 10, SALIDA, tarifa(t, "Standard", "ARS", "45000.104"));

        assertEquals(new BigDecimal("130000.11"), precioVuelo.cotizar(List.of(IDA), List.of(t), 1).precioPorPasajero());
    }
}
