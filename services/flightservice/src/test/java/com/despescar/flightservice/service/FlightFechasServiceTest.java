package com.despescar.flightservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.flightservice.enums.FlightStatus;
import com.despescar.flightservice.repository.AirlineRepository;
import com.despescar.flightservice.repository.AirportRepository;
import com.despescar.flightservice.repository.FareRepository;
import com.despescar.flightservice.repository.FlightRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightFechasServiceTest {

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

    @Test
    void devuelveCadaDiaUnaSolaVezYEnOrden() {
        LocalDate desde = LocalDate.of(2026, 10, 6);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        when(flightRepository.findSalidasConLugar("AEP", "COR", FlightStatus.SCHEDULED, desde.atStartOfDay(),
                hasta.plusDays(1).atStartOfDay())).thenReturn(List.of(
                LocalDateTime.of(2026, 10, 22, 7, 0), LocalDateTime.of(2026, 10, 19, 8, 0),
                LocalDateTime.of(2026, 10, 19, 20, 0)));

        assertEquals(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 22)),
                flightService.fechasConVuelos(" aep ", "cor", desde, hasta));
    }

    @Test
    void elRangoSeAcotaA90Dias() {
        LocalDate desde = LocalDate.of(2026, 10, 6);

        flightService.fechasConVuelos("AEP", "COR", desde, desde.plusDays(400));

        verify(flightRepository).findSalidasConLugar("AEP", "COR", FlightStatus.SCHEDULED, desde.atStartOfDay(),
                desde.plusDays(FlightService.MAX_DIAS_FECHAS + 1).atStartOfDay());
    }
}
