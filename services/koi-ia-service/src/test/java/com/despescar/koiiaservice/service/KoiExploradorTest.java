package com.despescar.koiiaservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.koiiaservice.client.dto.AirportResponse;
import com.despescar.koiiaservice.dto.response.KoiRecommendationResponse;
import com.despescar.koiiaservice.enums.TipoOpcion;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import com.despescar.koiiaservice.recomendador.HabitacionCandidata;
import com.despescar.koiiaservice.recomendador.HotelCandidato;
import com.despescar.koiiaservice.recomendador.VueloCandidato;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KoiExploradorTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 5);
    private static final LocalDate D19 = LocalDate.of(2026, 10, 19);
    private static final LocalDate D22 = LocalDate.of(2026, 10, 22);
    private static final String BARILOCHE = "San Carlos de Bariloche";

    private final KoiCatalogo catalogo = mock(KoiCatalogo.class);
    private final KoiExplorador explorador = new KoiExplorador(catalogo);

    private static AirportResponse aeropuerto(String code, String city) {
        AirportResponse a = new AirportResponse();
        a.setCode(code);
        a.setCity(city);
        return a;
    }

    private static UUID id(String clave) {
        return UUID.nameUUIDFromBytes(clave.getBytes());
    }

    private static KoiCatalogo.VuelosCandidatos vuelos(String clave, String tarifa) {
        VueloCandidato ida = new VueloCandidato(id(clave + "-ida"), id(clave + "-t1"), "Flybondi", "FO1",
                LocalDateTime.of(2026, 10, 19, 8, 0), LocalDateTime.of(2026, 10, 19, 10, 0), new BigDecimal(tarifa));
        VueloCandidato vuelta = new VueloCandidato(id(clave + "-vuelta"), id(clave + "-t2"), "Flybondi", "FO2",
                LocalDateTime.of(2026, 10, 22, 13, 0), LocalDateTime.of(2026, 10, 22, 15, 0), new BigDecimal(tarifa));
        return new KoiCatalogo.VuelosCandidatos(List.of(ida), List.of(vuelta));
    }

    private static List<HotelCandidato> hoteles(String ciudad, String porNoche) {
        return List.of(new HotelCandidato(id(ciudad + "-h"), "Hotel " + ciudad, ciudad, 4, null,
                List.of(new HabitacionCandidata(id(ciudad + "-d"), "Doble", 2, new BigDecimal(porNoche), 3))));
    }

    private static PedidoExploracion pedido(String presupuesto, int viajeros, LocalDate fechaIda, Integer noches) {
        return new PedidoExploracion(new BigDecimal(presupuesto), viajeros, "Buenos Aires", fechaIda, null, noches,
                HOY);
    }

    /** La ruta tiene vuelo ese día: el catálogo lo devuelve si cae en el rango pedido. */
    private void ruta(String origen, String destino, LocalDate fecha) {
        when(catalogo.fechasConVuelo(eq(origen), eq(destino), any(), any())).thenAnswer(inv -> {
            LocalDate desde = inv.getArgument(2);
            LocalDate hasta = inv.getArgument(3);
            return fecha.isBefore(desde) || fecha.isAfter(hasta) ? List.<LocalDate>of() : List.of(fecha);
        });
    }

    @BeforeEach
    void setUp() {
        when(catalogo.destinos()).thenReturn(List.of("Buenos Aires", "Córdoba", "Mendoza", BARILOCHE));
        when(catalogo.aeropuertos()).thenReturn(List.of(aeropuerto("AEP", "Buenos Aires"),
                aeropuerto("EZE", "Buenos Aires"), aeropuerto("COR", "Córdoba"), aeropuerto("MDZ", "Mendoza"),
                aeropuerto("BRC", BARILOCHE)));
        for (String codigo : List.of("COR", "MDZ", "BRC")) {
            ruta("AEP", codigo, D19);
            ruta(codigo, "AEP", D22);
        }
        // Córdoba 100+100 de vuelo + 3 noches x 1000 = 3200; Mendoza 5100; Bariloche 1.000.400
        doReturn(vuelos("COR", "100")).when(catalogo).vuelos(anyString(), eq("Córdoba"), any(), any(), anyInt());
        doReturn(hoteles("Córdoba", "1000")).when(catalogo).hoteles(eq("Córdoba"), any(), any(), anyInt());
        doReturn(vuelos("MDZ", "300")).when(catalogo).vuelos(anyString(), eq("Mendoza"), any(), any(), anyInt());
        doReturn(hoteles("Mendoza", "1500")).when(catalogo).hoteles(eq("Mendoza"), any(), any(), anyInt());
        doReturn(vuelos("BRC", "200")).when(catalogo).vuelos(anyString(), eq(BARILOCHE), any(), any(), anyInt());
        doReturn(hoteles(BARILOCHE, "333333.33")).when(catalogo).hoteles(eq(BARILOCHE), any(), any(), anyInt());
    }

    @Test
    void proponeCombosDeDestinosDistintosDentroDelPresupuestoSinElOrigen() {
        List<KoiRecommendationResponse> opciones = explorador.explorar(pedido("10000", 1, null, null));

        assertEquals(List.of("Córdoba", "Mendoza"), opciones.stream().map(o -> o.hotel().ciudad()).toList());
        assertTrue(opciones.stream().allMatch(o -> o.tipo() == TipoOpcion.COMBO && o.vuelo() != null
                && o.hotel() != null && o.excedeEn() == null));
        assertTrue(opciones.stream().allMatch(o -> o.total().compareTo(new BigDecimal("10000")) <= 0));
        assertTrue(opciones.get(0).motivo().contains("Te quedan $ 6.800"), opciones.get(0).motivo());
        verify(catalogo, never()).hoteles(eq("Buenos Aires"), any(), any(), anyInt());
    }

    @Test
    void sinFechasBuscaEnLaVentanaConVuelosRealesYTresNoches() {
        explorador.explorar(pedido("10000", 1, null, null));

        verify(catalogo).vuelos("Buenos Aires", "Córdoba", D19, D22, 1);
        verify(catalogo).hoteles("Córdoba", D19, D22, 1);
    }

    @Test
    void siLaFechaPedidaNoTieneVuelosUsaLaMasCercanaConLasNochesParecidas() {
        // un finde (viernes 9, 2 noches) sin vuelos: cae al 19 con vuelta el 22
        explorador.explorar(pedido("10000", 2, LocalDate.of(2026, 10, 9), 2));

        verify(catalogo).vuelos("Buenos Aires", "Córdoba", D19, D22, 2);
    }

    @Test
    void siNingunaEntraMuestraLasDosMasCercanasConLoQueSePasan() {
        List<KoiRecommendationResponse> opciones = explorador.explorar(pedido("1000", 1, null, null));

        assertEquals(2, opciones.size());
        assertEquals("Córdoba", opciones.get(0).hotel().ciudad());
        assertEquals(new BigDecimal("2200.00"), opciones.get(0).excedeEn());
        assertNotNull(opciones.get(1).excedeEn());
    }

    @Test
    void unDestinoQueFallaSeSaltaYSeSigueConLosDemas() {
        doThrow(new KoiCatalogUnavailableException("caído", null))
                .when(catalogo).vuelos(anyString(), eq("Mendoza"), any(), any(), anyInt());

        List<KoiRecommendationResponse> opciones = explorador.explorar(pedido("10000", 1, null, null));

        assertEquals(List.of("Córdoba"), opciones.stream().map(o -> o.hotel().ciudad()).toList());
        assertNull(opciones.get(0).excedeEn());
    }

    @Test
    void siTodosLosDestinosFallanElCatalogoEstaCaido() {
        doThrow(new KoiCatalogUnavailableException("caído", null))
                .when(catalogo).vuelos(anyString(), anyString(), any(), any(), anyInt());

        assertThrows(KoiCatalogUnavailableException.class, () -> explorador.explorar(pedido("10000", 1, null, null)));
    }

    @Test
    void sinDestinosConVuelosNoHayOpciones() {
        when(catalogo.fechasConVuelo(anyString(), anyString(), any(), any())).thenReturn(List.of());

        assertTrue(explorador.explorar(pedido("10000", 1, null, null)).isEmpty());
        verify(catalogo, never()).vuelos(anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void siConLasFechasPedidasNoHayNadaReintentaConLaVentanaFlexible() {
        // del 25 al 27 de noviembre no hay vuelos ni en los 14 días siguientes: cae al 19/10 por 3 noches
        List<KoiRecommendationResponse> opciones =
                explorador.explorar(pedido("10000", 2, LocalDate.of(2026, 11, 25), 2));

        assertEquals(List.of("Córdoba", "Mendoza"), opciones.stream().map(o -> o.hotel().ciudad()).toList());
        verify(catalogo).vuelos("Buenos Aires", "Córdoba", D19, D22, 2);
    }

    @Test
    void siElMesPedidoNoTieneVuelosReintentaConLaVentanaFlexible() {
        List<KoiRecommendationResponse> opciones = explorador.explorar(new PedidoExploracion(
                new BigDecimal("10000"), 1, "Buenos Aires", null, YearMonth.of(2026, 11), null, HOY));

        assertEquals(2, opciones.size());
        verify(catalogo).vuelos("Buenos Aires", "Córdoba", D19, D22, 1);
    }

    @Test
    void preguntaLasFechasDeCadaRutaEnLaVentanaYLasVueltasCercaDeLasIdas() {
        explorador.explorar(pedido("10000", 1, null, null));

        // idas: de mañana a 60 días, por cada aeropuerto de origen; vueltas: desde el día siguiente
        // a la ida encontrada hasta las noches por defecto más el margen
        verify(catalogo).fechasConVuelo("AEP", "COR", HOY.plusDays(1), HOY.plusDays(60));
        verify(catalogo).fechasConVuelo("EZE", "COR", HOY.plusDays(1), HOY.plusDays(60));
        verify(catalogo).fechasConVuelo("COR", "AEP", D19.plusDays(1), D19.plusDays(6));
        verify(catalogo).fechasConVuelo("COR", "EZE", D19.plusDays(1), D19.plusDays(6));
    }

    @Test
    void siUnDestinoNoTieneIdasNoSePreguntanSusVueltas() {
        when(catalogo.fechasConVuelo(eq("AEP"), eq("MDZ"), any(), any())).thenReturn(List.of());

        explorador.explorar(pedido("10000", 1, null, null));

        verify(catalogo, never()).fechasConVuelo(eq("MDZ"), anyString(), any(), any());
        verify(catalogo, never()).vuelos(anyString(), eq("Mendoza"), any(), any(), anyInt());
    }

    @Test
    void siLasFechasDeUnDestinoNoSePuedenConsultarSeSaltaYSeSigue() {
        when(catalogo.fechasConVuelo(eq("AEP"), eq("MDZ"), any(), any()))
                .thenThrow(new KoiCatalogUnavailableException("caído", null));

        List<KoiRecommendationResponse> opciones = explorador.explorar(pedido("10000", 1, null, null));

        assertEquals(List.of("Córdoba"), opciones.stream().map(o -> o.hotel().ciudad()).toList());
    }

    @Test
    void siNingunaRutaSePuedeConsultarElCatalogoEstaCaido() {
        when(catalogo.fechasConVuelo(anyString(), anyString(), any(), any()))
                .thenThrow(new KoiCatalogUnavailableException("caído", null));

        assertThrows(KoiCatalogUnavailableException.class, () -> explorador.explorar(pedido("10000", 1, null, null)));
    }
}
