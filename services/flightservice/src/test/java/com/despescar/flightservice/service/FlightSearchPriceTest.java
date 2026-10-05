package com.despescar.flightservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.despescar.flightservice.dto.flights.response.FlightSearchResponse;
import com.despescar.flightservice.dto.flights.response.PriceDto;
import com.despescar.flightservice.entity.Airline;
import com.despescar.flightservice.entity.Airport;
import com.despescar.flightservice.entity.Fare;
import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.repository.AirlineRepository;
import com.despescar.flightservice.repository.AirportRepository;
import com.despescar.flightservice.repository.FareRepository;
import com.despescar.flightservice.repository.FlightRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La búsqueda muestra por pasajero lo mismo que después cobra el carrito. */
@ExtendWith(MockitoExtension.class)
class FlightSearchPriceTest {

    @Mock
    private FlightRepository flightRepository;
    @Mock
    private AirlineRepository airlineRepository;
    @Mock
    private AirportRepository airportRepository;
    @Mock
    private FareRepository fareRepository;

    @InjectMocks
    private FlightService flightService;

    private static Fare tarifa(String nombre, String precio) {
        return Fare.builder()
                .id(UUID.randomUUID())
                .name(nombre)
                .type(nombre.toUpperCase())
                .currency("ARS")
                .baseFare(new BigDecimal(precio))
                .taxesAndFees(BigDecimal.ZERO)
                .transparentFinalPrice(new BigDecimal(precio))
                .build();
    }

    private static Flight vuelo(String precio, List<Fare> tarifas) {
        LocalDateTime salida = LocalDateTime.of(2026, 10, 19, 8, 0);
        return Flight.builder()
                .id(UUID.randomUUID())
                .flightNumber("AR1004")
                .airline(Airline.builder().name("Aerolíneas Argentinas").code("AR").build())
                .originAirport(Airport.builder().code("AEP").city("Buenos Aires").build())
                .destinationAirport(Airport.builder().code("BRC").city("San Carlos de Bariloche").build())
                .departureTime(salida)
                .arrivalTime(salida.plusMinutes(150))
                .price(new BigDecimal(precio))
                .availableSeats(150)
                .fares(tarifas)
                .build();
    }

    @Test
    void laDuracionSaleDeLosHorariosDelVuelo() {
        when(flightRepository.findFlightsForSearch(eq("AEP"), eq("BRC"), any(), any()))
                .thenReturn(List.of(vuelo("160000.00", List.of(tarifa("Light", "0.00")))));
        FlightSearchResponse respuesta = flightService.searchFlights("AEP", "BRC", LocalDate.of(2026, 10, 19), null, 1);

        assertEquals(150, respuesta.getDepartureFlights().get(0).getItinerary().getDurationMinutes());
    }

    private PriceDto buscarPrecio(Flight flight) {
        when(flightRepository.findFlightsForSearch(eq("AEP"), eq("BRC"), any(), any())).thenReturn(List.of(flight));
        FlightSearchResponse respuesta = flightService.searchFlights("AEP", "BRC", LocalDate.of(2026, 10, 19), null, 2);
        return respuesta.getDepartureFlights().get(0).getPrice();
    }

    @Test
    void elPrecioFinalEsElDelVueloMasLaTarifaMasBarata() {
        PriceDto precio = buscarPrecio(vuelo("160000.00", List.of(tarifa("Standard", "45000.00"), tarifa("Light", "0.00"))));

        assertEquals(0, new BigDecimal("160000.00").compareTo(precio.getTransparentFinalPrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(precio.getTaxesAndFees()));
        assertEquals(0, new BigDecimal("160000.00").compareTo(precio.getBaseFare()));
        assertConsistente(precio);
        assertEquals("ARS", precio.getCurrency());
    }

    @Test
    void siLaTarifaMasBarataCuestaSeSuma() {
        PriceDto precio = buscarPrecio(vuelo("85000.00", List.of(tarifa("Standard", "45000.00"))));

        assertEquals(0, new BigDecimal("130000.00").compareTo(precio.getTransparentFinalPrice()));
        assertEquals(0, new BigDecimal("85000.00").compareTo(precio.getBaseFare()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(precio.getTaxesAndFees()));
        assertConsistente(precio);
    }

    @Test
    void sinTarifasElPrecioFinalEsElDelVuelo() {
        PriceDto precio = buscarPrecio(vuelo("85000.00", List.of()));

        assertEquals(0, new BigDecimal("85000.00").compareTo(precio.getTransparentFinalPrice()));
        assertConsistente(precio);
    }

    @Test
    void conTarifasNulasElPrecioFinalEsElDelVuelo() {
        Flight flight = vuelo("85000.00", List.of());
        flight.setFares(null);

        PriceDto precio = buscarPrecio(flight);

        assertEquals(0, new BigDecimal("85000.00").compareTo(precio.getTransparentFinalPrice()));
        assertConsistente(precio);
    }

    @Test
    void elPrecioSeNormalizaAEscalaDosHalfUp() {
        PriceDto precio = buscarPrecio(vuelo("85000.005", List.of(tarifa("Standard", "45000.1"))));

        assertEquals(new BigDecimal("85000.01"), precio.getBaseFare());
        assertEquals(new BigDecimal("45000.10"), precio.getTaxesAndFees());
        assertEquals(new BigDecimal("130000.11"), precio.getTransparentFinalPrice());
    }

    private static void assertConsistente(PriceDto precio) {
        assertEquals(2, precio.getTransparentFinalPrice().scale());
        assertEquals(precio.getTransparentFinalPrice(), precio.getBaseFare().add(precio.getTaxesAndFees()));
    }
}
