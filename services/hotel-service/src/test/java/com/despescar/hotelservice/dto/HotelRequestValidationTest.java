package com.despescar.hotelservice.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.hotelservice.dto.request.HabitacionRequest;
import com.despescar.hotelservice.dto.request.HotelRequest;
import com.despescar.hotelservice.entity.Servicio;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HotelRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static HabitacionRequest habitacion() {
        return new HabitacionRequest("Doble", null, 2, new BigDecimal("100.00"), 3, List.of("https://img/a.jpg"));
    }

    private static HotelRequest request(List<TramoDto> politica, List<HabitacionRequest> habitaciones,
                                        List<String> imagenes) {
        return new HotelRequest("Hotel", "Córdoba", "Argentina", "Calle 1", 4, null, false, imagenes,
                Set.of(Servicio.values()[0]), politica, null, null, null, habitaciones);
    }

    private static List<TramoDto> politica() {
        return List.of(new TramoDto(48, 100));
    }

    @Test
    void unRequestValidoNoTieneViolaciones() {
        assertTrue(VALIDATOR.validate(request(politica(), List.of(habitacion()), List.of("https://img/a.jpg"))).isEmpty());
    }

    @Test
    void unTramoNuloEsInvalido() {
        List<TramoDto> tramos = new ArrayList<>();
        tramos.add(null);
        assertFalse(VALIDATOR.validate(request(tramos, List.of(habitacion()), List.of())).isEmpty());
    }

    @Test
    void unTramoSinPorcentajeEsInvalido() {
        assertFalse(VALIDATOR.validate(
                request(List.of(new TramoDto(48, null)), List.of(habitacion()), List.of())).isEmpty());
    }

    @Test
    void unaHabitacionNulaEsInvalida() {
        List<HabitacionRequest> habitaciones = Arrays.asList((HabitacionRequest) null);
        assertFalse(VALIDATOR.validate(request(politica(), habitaciones, List.of())).isEmpty());
    }

    @Test
    void unaImagenHttpEsInvalida() {
        assertFalse(VALIDATOR.validate(
                request(politica(), List.of(habitacion()), List.of("http://img/a.jpg"))).isEmpty());
    }
}
