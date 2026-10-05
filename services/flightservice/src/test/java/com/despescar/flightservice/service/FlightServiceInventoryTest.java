package com.despescar.flightservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.exception.FlightNotFoundException;
import com.despescar.flightservice.repository.AirlineRepository;
import com.despescar.flightservice.repository.AirportRepository;
import com.despescar.flightservice.repository.FareRepository;
import com.despescar.flightservice.repository.FlightRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightServiceInventoryTest {

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

    private Flight flight;

    @BeforeEach
    void setUp() {
        flight = new Flight();
        flight.setFlightNumber("AR1234");
        flight.setAvailableSeats(10);
    }

    @Test
    void deltaNegativoReservaAsientos() {
        when(flightRepository.findByFlightNumber("AR1234")).thenReturn(Optional.of(flight));

        flightService.adjustSeats("AR1234", -3);

        assertEquals(7, flight.getAvailableSeats());
        verify(flightRepository).save(flight);
    }

    @Test
    void deltaPositivoLiberaAsientos() {
        when(flightRepository.findByFlightNumber("AR1234")).thenReturn(Optional.of(flight));

        flightService.adjustSeats("AR1234", 2);

        assertEquals(12, flight.getAvailableSeats());
    }

    @Test
    void sePuedenReservarTodosLosAsientosPeroNoMas() {
        when(flightRepository.findByFlightNumber("AR1234")).thenReturn(Optional.of(flight));

        flightService.adjustSeats("AR1234", -10);
        assertEquals(0, flight.getAvailableSeats());

        assertThrows(IllegalStateException.class, () -> flightService.adjustSeats("AR1234", -1));
        assertEquals(0, flight.getAvailableSeats());
    }

    @Test
    void sinAsientosSuficientesNoSeGuardaNada() {
        when(flightRepository.findByFlightNumber("AR1234")).thenReturn(Optional.of(flight));

        assertThrows(IllegalStateException.class, () -> flightService.adjustSeats("AR1234", -11));

        assertEquals(10, flight.getAvailableSeats());
        verify(flightRepository, never()).save(any());
    }

    @Test
    void conUnNumeroDeVueloInexistenteLanzaNotFound() {
        when(flightRepository.findByFlightNumber("XX0000")).thenReturn(Optional.empty());

        assertThrows(FlightNotFoundException.class, () -> flightService.adjustSeats("XX0000", -1));
    }
}
