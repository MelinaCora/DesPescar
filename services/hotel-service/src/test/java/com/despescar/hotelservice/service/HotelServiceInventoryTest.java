package com.despescar.hotelservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.exception.HotelNotFoundException;
import com.despescar.hotelservice.mapper.HotelMapper;
import com.despescar.hotelservice.repository.HotelRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HotelServiceInventoryTest {

    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private HotelMapper hotelMapper;

    @InjectMocks
    private HotelService hotelService;

    private final UUID id = UUID.randomUUID();
    private Hotel hotel;

    @BeforeEach
    void setUp() {
        hotel = new Hotel();
        hotel.setHabitacionesDisponibles(5);
    }

    @Test
    void deltaNegativoReservaUnaHabitacion() {
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));

        hotelService.adjustRooms(id, -1);

        assertEquals(4, hotel.getHabitacionesDisponibles());
        verify(hotelRepository).save(hotel);
    }

    @Test
    void deltaPositivoLiberaHabitaciones() {
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));

        hotelService.adjustRooms(id, 2);

        assertEquals(7, hotel.getHabitacionesDisponibles());
    }

    @Test
    void sinHabitacionesSuficientesNoSeGuardaNada() {
        when(hotelRepository.findById(id)).thenReturn(Optional.of(hotel));

        assertThrows(IllegalStateException.class, () -> hotelService.adjustRooms(id, -6));

        assertEquals(5, hotel.getHabitacionesDisponibles());
        verify(hotelRepository, never()).save(any());
    }

    @Test
    void conUnHotelInexistenteLanzaNotFound() {
        when(hotelRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(HotelNotFoundException.class, () -> hotelService.adjustRooms(id, -1));
    }
}
