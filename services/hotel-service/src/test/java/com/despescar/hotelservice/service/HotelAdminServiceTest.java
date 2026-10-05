package com.despescar.hotelservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.hotelservice.dto.TramoDto;
import com.despescar.hotelservice.dto.request.HabitacionRequest;
import com.despescar.hotelservice.dto.request.HotelRequest;
import com.despescar.hotelservice.dto.response.HabitacionDetalleResponse;
import com.despescar.hotelservice.dto.response.HotelDetalleResponse;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.Servicio;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import com.despescar.hotelservice.mapper.HotelMapper;
import com.despescar.hotelservice.repository.HotelRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HotelAdminServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    private HotelAdminService service;

    @BeforeEach
    void setUp() {
        service = new HotelAdminService(hotelRepository, new HotelMapper());
    }

    private static HabitacionRequest habitacion() {
        return new HabitacionRequest("  Doble  ", "Vista al lago", 2, new BigDecimal("100000"), 3,
                List.of("https://img/doble.jpg"));
    }

    private static HotelRequest request(List<TramoDto> politica, LocalTime hora, String zona) {
        return new HotelRequest("  Sheraton  ", " Córdoba ", " Argentina ", " Calle 1 ", 4, "desc", false,
                List.of("https://img/principal.jpg"), Set.of(Servicio.values()[0]), politica, hora, zona, 7L,
                List.of(habitacion()));
    }

    private static List<TramoDto> politicaValida() {
        return List.of(new TramoDto(48, 100), new TramoDto(24, 50));
    }

    @Test
    void guardaElHotelConValoresPorDefectoYHabitaciones() {
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(i -> i.getArgument(0));

        HotelDetalleResponse respuesta = service.crear(request(politicaValida(), null, null));

        ArgumentCaptor<Hotel> captor = ArgumentCaptor.forClass(Hotel.class);
        verify(hotelRepository).save(captor.capture());
        Hotel hotel = captor.getValue();
        assertEquals("Sheraton", hotel.getNombre());
        assertEquals("Córdoba", hotel.getCiudad());
        assertEquals("Argentina", hotel.getPais());
        assertEquals("Calle 1", hotel.getDireccion());
        assertEquals(Hotel.HORA_CHECK_IN_POR_DEFECTO, hotel.getHoraCheckIn());
        assertEquals(Hotel.ZONA_POR_DEFECTO, hotel.getZonaHoraria());
        assertEquals(1, hotel.getHabitaciones().size());
        assertEquals("Doble", hotel.getHabitaciones().get(0).getNombre());
        assertSame(hotel, hotel.getHabitaciones().get(0).getHotel());

        assertEquals(1, respuesta.habitaciones().size());
        HabitacionDetalleResponse hab = respuesta.habitaciones().get(0);
        assertNull(hab.unidadesLibres());
        assertNull(hab.habitacionesNecesarias());
        assertNull(hab.precioTotal());
        assertNull(hab.disponible());
        assertNull(respuesta.noches());
        assertEquals(2, respuesta.politicaCancelacion().size());
    }

    @Test
    void politicaInvalidaNoSeGuarda() {
        // El reembolso aumenta a medida que se acerca la fecha.
        List<TramoDto> invalida = List.of(new TramoDto(48, 50), new TramoDto(24, 100));

        assertThrows(SolicitudInvalidaException.class, () -> service.crear(request(invalida, null, null)));
        verify(hotelRepository, never()).save(any());
    }

    @Test
    void zonaHorariaDesconocidaNoSeGuarda() {
        assertThrows(SolicitudInvalidaException.class,
                () -> service.crear(request(politicaValida(), null, "Marte/Olympus")));
        verify(hotelRepository, never()).save(any());
    }

    @Test
    void conservaZonaYHoraExplicitas() {
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(i -> i.getArgument(0));

        HotelDetalleResponse respuesta = service.crear(request(politicaValida(), LocalTime.of(15, 0), "Europe/Madrid"));

        assertEquals(LocalTime.of(15, 0), respuesta.horaCheckIn());
        assertEquals("Europe/Madrid", respuesta.zonaHoraria());
    }
}
