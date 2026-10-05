package com.despescar.hotelservice.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.hotelservice.entity.TramoCancelacion;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DominioHotelTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 1);
    private static final LocalDate D10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D13 = LocalDate.of(2026, 11, 13);

    @Test
    void normalizaSinTildesNiMayusculas() {
        assertEquals("cordoba", TextoBusqueda.normalizar("  Córdoba "));
        assertEquals("san carlos de bariloche", TextoBusqueda.normalizar("San Carlos de BARILOCHE"));
        assertEquals("", TextoBusqueda.normalizar(null));
    }

    @Test
    void rangoSinFechasEsVacio() {
        assertTrue(RangoEstadia.de(null, null, HOY).isEmpty());
    }

    @Test
    void rangoValidoCuentaNoches() {
        assertEquals(3, RangoEstadia.de(D10, D13, HOY).orElseThrow().noches());
    }

    @Test
    void rangoInvalidoSeRechaza() {
        assertThrows(SolicitudInvalidaException.class, () -> RangoEstadia.de(D10, null, HOY));
        assertThrows(SolicitudInvalidaException.class, () -> RangoEstadia.de(D13, D10, HOY));
        assertThrows(SolicitudInvalidaException.class, () -> RangoEstadia.de(D10, D10, HOY));
        assertThrows(SolicitudInvalidaException.class, () -> RangoEstadia.de(HOY.minusDays(1), D10, HOY));
        assertThrows(SolicitudInvalidaException.class, () -> RangoEstadia.de(D10, D10.plusDays(31), HOY));
        assertEquals(30, RangoEstadia.de(D10, D10.plusDays(30), HOY).orElseThrow().noches());
    }

    @Test
    void sinOcupacionesEstanTodasLibres() {
        assertEquals(5, Disponibilidad.unidadesLibres(5, List.of(), D10, D13));
    }

    @Test
    void cuentaLaNocheMasOcupada() {
        List<Disponibilidad.Ocupacion> ocupaciones = List.of(
                new Disponibilidad.Ocupacion(D10, D10.plusDays(1), 2),              // noche del 10
                new Disponibilidad.Ocupacion(D10.plusDays(1), D13.plusDays(5), 3)); // del 11 en adelante
        assertEquals(2, Disponibilidad.unidadesLibres(5, ocupaciones, D10, D13));
    }

    @Test
    void elCheckOutNoOcupaEsaNoche() {
        List<Disponibilidad.Ocupacion> ocupaciones = List.of(
                new Disponibilidad.Ocupacion(D10.minusDays(2), D10, 5));
        assertEquals(5, Disponibilidad.unidadesLibres(5, ocupaciones, D10, D13));
    }

    @Test
    void nuncaDevuelveNegativo() {
        List<Disponibilidad.Ocupacion> ocupaciones = List.of(new Disponibilidad.Ocupacion(D10, D13, 9));
        assertEquals(0, Disponibilidad.unidadesLibres(5, ocupaciones, D10, D13));
    }

    @Test
    void calculaHabitacionesNecesarias() {
        assertEquals(1, Disponibilidad.habitacionesNecesarias(2, 2));
        assertEquals(3, Disponibilidad.habitacionesNecesarias(5, 2));
        assertEquals(1, Disponibilidad.habitacionesNecesarias(1, 4));
    }

    @Test
    void precioEsNochesPorPrecioPorCantidad() {
        RangoEstadia rango = new RangoEstadia(D10, D13);
        assertEquals(new BigDecimal("6003.00"), PrecioEstadia.total(new BigDecimal("1000.50"), rango, 2));
    }

    @Test
    void cotizacionMarcaDisponibleSoloSiAlcanzanLasUnidades() {
        RangoEstadia rango = new RangoEstadia(D10, D13);
        List<Disponibilidad.Ocupacion> ocupaciones = List.of(new Disponibilidad.Ocupacion(D10, D13, 3));

        Cotizacion alcanza = Cotizacion.calcular(5, 2, new BigDecimal("100"), ocupaciones, rango, 4);
        assertEquals(2, alcanza.unidadesLibres());
        assertEquals(2, alcanza.habitacionesNecesarias());
        assertEquals(new BigDecimal("600.00"), alcanza.precioTotal());
        assertTrue(alcanza.disponible());

        Cotizacion noAlcanza = Cotizacion.calcular(5, 2, new BigDecimal("100"), ocupaciones, rango, 5);
        assertEquals(3, noAlcanza.habitacionesNecesarias());
        assertFalse(noAlcanza.disponible());
    }

    @Test
    void politicaValidaSeAceptaEnCualquierOrden() {
        PoliticaCancelacionValidator.validar(List.of(
                new TramoCancelacion(24, 50), new TramoCancelacion(72, 100), new TramoCancelacion(0, 0)));
    }

    @Test
    void politicaInvalidaSeRechaza() {
        assertThrows(SolicitudInvalidaException.class, () -> PoliticaCancelacionValidator.validar(List.of()));
        assertThrows(SolicitudInvalidaException.class, () -> PoliticaCancelacionValidator.validar(null));
        assertThrows(SolicitudInvalidaException.class,
                () -> PoliticaCancelacionValidator.validar(List.of(new TramoCancelacion(-1, 50))));
        assertThrows(SolicitudInvalidaException.class,
                () -> PoliticaCancelacionValidator.validar(List.of(new TramoCancelacion(10, 101))));
        assertThrows(SolicitudInvalidaException.class, () -> PoliticaCancelacionValidator.validar(
                List.of(new TramoCancelacion(24, 50), new TramoCancelacion(24, 20))));
        // Más cerca de la fecha no puede devolver más
        assertThrows(SolicitudInvalidaException.class, () -> PoliticaCancelacionValidator.validar(
                List.of(new TramoCancelacion(72, 50), new TramoCancelacion(24, 80))));
    }
}
