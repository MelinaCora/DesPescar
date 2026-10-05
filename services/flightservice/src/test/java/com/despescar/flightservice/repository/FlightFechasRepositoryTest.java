package com.despescar.flightservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.despescar.flightservice.entity.Airline;
import com.despescar.flightservice.entity.Airport;
import com.despescar.flightservice.entity.Flight;
import com.despescar.flightservice.enums.FlightStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** La consulta de fechas trae solo las salidas de la ruta, programadas, con lugar y dentro del rango. */
@DataJpaTest
class FlightFechasRepositoryTest {

    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private AirportRepository airportRepository;
    @Autowired
    private AirlineRepository airlineRepository;

    private Airline aerolinea;
    private Airport aep;
    private Airport cor;
    private int numero;

    @BeforeEach
    void setUp() {
        aerolinea = airlineRepository.save(Airline.builder().name("Prueba").code("PR").build());
        aep = airportRepository.save(Airport.builder().name("Aeroparque").code("AEP").city("Buenos Aires")
                .country("Argentina").build());
        cor = airportRepository.save(Airport.builder().name("Córdoba").code("COR").city("Córdoba")
                .country("Argentina").build());
    }

    private void vuelo(Airport origen, Airport destino, LocalDateTime salida, int asientos, FlightStatus estado) {
        flightRepository.save(Flight.builder().flightNumber("PR" + (++numero)).airline(aerolinea)
                .originAirport(origen).destinationAirport(destino).departureTime(salida)
                .arrivalTime(salida.plusHours(2)).price(new BigDecimal("100")).availableSeats(asientos)
                .status(estado).build());
    }

    @Test
    void traeLasSalidasProgramadasConLugarDeLaRutaEnElRango() {
        LocalDateTime d10 = LocalDateTime.of(2026, 10, 10, 8, 0);
        vuelo(aep, cor, d10, 5, FlightStatus.SCHEDULED);
        vuelo(aep, cor, d10.plusHours(6), 5, FlightStatus.SCHEDULED);
        vuelo(aep, cor, d10.plusDays(2).withHour(23), 1, FlightStatus.SCHEDULED);
        vuelo(aep, cor, d10.plusDays(1), 0, FlightStatus.SCHEDULED);      // sin lugar
        vuelo(aep, cor, d10.plusDays(1), 5, FlightStatus.CANCELLED);      // cancelado
        vuelo(cor, aep, d10.plusDays(1), 5, FlightStatus.SCHEDULED);      // ruta inversa
        vuelo(aep, cor, d10.minusDays(1).withHour(23), 5, FlightStatus.SCHEDULED); // antes del rango
        vuelo(aep, cor, d10.plusDays(3).withHour(0), 5, FlightStatus.SCHEDULED);   // después del rango

        List<LocalDateTime> salidas = flightRepository.findSalidasConLugar("AEP", "COR", FlightStatus.SCHEDULED,
                LocalDateTime.of(2026, 10, 10, 0, 0), LocalDateTime.of(2026, 10, 13, 0, 0));

        assertEquals(List.of(d10, d10.plusHours(6), d10.plusDays(2).withHour(23)), salidas);
    }
}
