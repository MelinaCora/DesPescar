package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.despescar.reservationservice.exception.BookingException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RepartoGrupoTest {

    private static List<BigDecimal> montos(String... valores) {
        return Arrays.stream(valores).map(BigDecimal::new).toList();
    }

    private static BigDecimal suma(List<BigDecimal> montos) {
        return montos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void partesIgualesConElRestoDeCentavosParaElOrganizador() {
        List<BigDecimal> partes = RepartoGrupo.iguales(new BigDecimal("1060000.00"), 3);

        assertEquals(montos("353333.34", "353333.33", "353333.33"), partes);
        assertEquals(new BigDecimal("1060000.00"), suma(partes));
    }

    @Test
    void sinRestoTodasValenLoMismo() {
        assertEquals(montos("500.00", "500.00"), RepartoGrupo.iguales(new BigDecimal("1000"), 2));
    }

    @Test
    void elRestoPuedeSerDeVariosCentavos() {
        List<BigDecimal> partes = RepartoGrupo.iguales(new BigDecimal("1000.07"), 10);

        assertEquals(new BigDecimal("100.07"), partes.get(0));
        assertEquals(new BigDecimal("100.00"), partes.get(9));
        assertEquals(new BigDecimal("1000.07"), suma(partes));
    }

    @Test
    void laCantidadVaDeDosADiez() {
        BookingException uno = assertThrows(BookingException.class, () -> RepartoGrupo.iguales(new BigDecimal("5000"), 1));
        BookingException once = assertThrows(BookingException.class, () -> RepartoGrupo.iguales(new BigDecimal("5000"), 11));

        assertEquals("CANTIDAD_PARTES_INVALIDA", uno.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, uno.getStatus());
        assertEquals("CANTIDAD_PARTES_INVALIDA", once.getCodigo());
    }

    @Test
    void cadaParteValeAlMenosCienPesos() {
        BookingException ex = assertThrows(BookingException.class, () -> RepartoGrupo.iguales(new BigDecimal("299.99"), 3));

        assertEquals("PARTES_DEMASIADO_CHICAS", ex.getCodigo());
        assertEquals(montos("100.00", "100.00", "100.00"), RepartoGrupo.iguales(new BigDecimal("300"), 3));
    }

    @Test
    void montosEditadosQueSumanElTotalSeAceptanConEscalaDos() {
        List<BigDecimal> validados = RepartoGrupo.validarMontos(montos("400000", "330000.5", "330000.50"),
                new BigDecimal("1060001.00"));

        assertEquals(montos("400000.00", "330000.50", "330000.50"), validados);
    }

    @Test
    void montosQueNoSumanElTotalSeRechazan() {
        BookingException ex = assertThrows(BookingException.class,
                () -> RepartoGrupo.validarMontos(montos("500.00", "499.99"), new BigDecimal("1000.00")));

        assertEquals("MONTOS_NO_SUMAN_TOTAL", ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void montosConTresDecimalesNegativosNulosOMenoresAlMinimoSonInvalidos() {
        BigDecimal total = new BigDecimal("1000.00");

        assertEquals("MONTOS_INVALIDOS", assertThrows(BookingException.class,
                () -> RepartoGrupo.validarMontos(montos("500.005", "499.995"), total)).getCodigo());
        assertEquals("MONTOS_INVALIDOS", assertThrows(BookingException.class,
                () -> RepartoGrupo.validarMontos(montos("1100.00", "-100.00"), total)).getCodigo());
        assertEquals("MONTOS_INVALIDOS", assertThrows(BookingException.class,
                () -> RepartoGrupo.validarMontos(montos("950.00", "50.00"), total)).getCodigo());
        assertEquals("MONTOS_INVALIDOS", assertThrows(BookingException.class,
                () -> RepartoGrupo.validarMontos(Arrays.asList(new BigDecimal("1000.00"), null), total)).getCodigo());
    }

    @Test
    void laListaDeMontosTambienRespetaLaCantidad() {
        assertEquals("CANTIDAD_PARTES_INVALIDA", assertThrows(BookingException.class,
                () -> RepartoGrupo.validarMontos(montos("1000.00"), new BigDecimal("1000.00"))).getCodigo());
        assertEquals("VALIDACION", assertThrows(BookingException.class,
                () -> RepartoGrupo.validarMontos(null, new BigDecimal("1000.00"))).getCodigo());
    }
}
