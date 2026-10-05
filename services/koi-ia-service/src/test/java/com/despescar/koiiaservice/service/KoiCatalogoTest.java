package com.despescar.koiiaservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.koiiaservice.client.CatalogoHotelesClient;
import com.despescar.koiiaservice.client.CatalogoVuelosClient;
import com.despescar.koiiaservice.client.dto.AirportResponse;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.Aerolinea;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.Itinerario;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.Precio;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.Tarifa;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.Tramo;
import com.despescar.koiiaservice.client.dto.BusquedaVuelosResponse.VueloBuscado;
import com.despescar.koiiaservice.client.dto.HotelDetalleResponse;
import com.despescar.koiiaservice.client.dto.HotelResumenResponse;
import com.despescar.koiiaservice.recomendador.HotelCandidato;
import com.despescar.koiiaservice.recomendador.VueloCandidato;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KoiCatalogoTest {

    private static final LocalDate IDA = LocalDate.of(2026, 11, 19);
    private static final LocalDate VUELTA = LocalDate.of(2026, 11, 22);

    private final CatalogoVuelosClient vuelosClient = mock(CatalogoVuelosClient.class);
    private final CatalogoHotelesClient hotelesClient = mock(CatalogoHotelesClient.class);
    private final KoiCatalogo catalogo = new KoiCatalogo(vuelosClient, hotelesClient);

    private static AirportResponse aeropuerto(String code, String city) {
        AirportResponse a = new AirportResponse();
        a.setCode(code);
        a.setCity(city);
        return a;
    }

    private static VueloBuscado vuelo(String numero, String salida, String llegada, Tarifa... tarifas) {
        return new VueloBuscado(UUID.randomUUID(), numero, new Aerolinea("Flybondi"),
                new Itinerario(new Tramo("AEP", salida), new Tramo("BRC", llegada)), List.of(tarifas));
    }

    private static Tarifa tarifa(UUID id, String precio) {
        return new Tarifa(id, "Light", new Precio("ARS", new BigDecimal(precio)));
    }

    @Test
    void buscaCadaParDeAeropuertosYTomaLaTarifaMasBarata() {
        UUID barata = UUID.randomUUID();
        when(vuelosClient.aeropuertos()).thenReturn(List.of(aeropuerto("EZE", "Buenos Aires"),
                aeropuerto("AEP", "Buenos Aires"), aeropuerto("BRC", "San Carlos de Bariloche")));
        when(vuelosClient.buscar(eq("AEP"), eq("BRC"), eq(IDA), eq(VUELTA), eq(2))).thenReturn(
                new BusquedaVuelosResponse(
                        List.of(vuelo("FO1045", "2026-11-19T08:00", "2026-11-19T10:30",
                                tarifa(UUID.randomUUID(), "170"), tarifa(barata, "130"))),
                        List.of(vuelo("FO1046", "2026-11-22T13:00", "2026-11-22T15:30",
                                tarifa(UUID.randomUUID(), "120")))));
        when(vuelosClient.buscar(eq("EZE"), eq("BRC"), eq(IDA), eq(VUELTA), eq(2)))
                .thenReturn(new BusquedaVuelosResponse(List.of(), List.of()));

        KoiCatalogo.VuelosCandidatos vuelos = catalogo.vuelos("Buenos Aires", "Bariloche", IDA, VUELTA, 2);

        assertEquals(1, vuelos.idas().size());
        VueloCandidato ida = vuelos.idas().get(0);
        assertEquals(barata, ida.fareId());
        assertEquals(new BigDecimal("130"), ida.precioTarifa());
        assertEquals(LocalDateTime.of(2026, 11, 19, 8, 0), ida.salida());
        assertEquals("FO1045", ida.numero());
        assertEquals(1, vuelos.vueltas().size());
    }

    @Test
    void unVueloSinTarifasOConFechaRaraSeDescarta() {
        assertTrue(KoiCatalogo.candidato(vuelo("X1", "2026-11-19T08:00", "2026-11-19T10:00")).isEmpty());
        assertTrue(KoiCatalogo.candidato(vuelo("X2", "mañana", "2026-11-19T10:00",
                tarifa(UUID.randomUUID(), "100"))).isEmpty());
    }

    @Test
    void unLugarSinAeropuertoNoBuscaVuelos() {
        when(vuelosClient.aeropuertos()).thenReturn(List.of(aeropuerto("BRC", "San Carlos de Bariloche")));

        KoiCatalogo.VuelosCandidatos vuelos = catalogo.vuelos("Rosario", "Bariloche", IDA, null, 1);

        assertTrue(vuelos.idas().isEmpty());
        verify(vuelosClient, never()).buscar(anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void traeElDetalleSoloDeLosHotelesDisponibles() {
        UUID disponible = UUID.randomUUID();
        UUID lleno = UUID.randomUUID();
        UUID habitacion = UUID.randomUUID();
        when(hotelesClient.buscar("Bariloche", IDA, VUELTA, 2)).thenReturn(List.of(
                new HotelResumenResponse(lleno, "Lleno", "San Carlos de Bariloche", 3, "https://img/l", false, null),
                new HotelResumenResponse(disponible, "Llao Llao", "San Carlos de Bariloche", 5, "https://img/1",
                        true, new BigDecimal("960000"))));
        when(hotelesClient.detalle(disponible, IDA, VUELTA, 2)).thenReturn(new HotelDetalleResponse(disponible,
                "Llao Llao", "San Carlos de Bariloche", 5, List.of("https://img/1"), 3L, List.of(
                new HotelDetalleResponse.Habitacion(habitacion, "Doble", 2, new BigDecimal("320000"), 4, true),
                new HotelDetalleResponse.Habitacion(UUID.randomUUID(), "Sin cotizar", 2, new BigDecimal("1"), null, null))));

        List<HotelCandidato> hoteles = catalogo.hoteles("Bariloche", IDA, VUELTA, 2);

        assertEquals(1, hoteles.size());
        HotelCandidato h = hoteles.get(0);
        assertEquals("Llao Llao", h.nombre());
        assertEquals("https://img/1", h.imagen());
        assertEquals(1, h.habitaciones().size());
        assertEquals(habitacion, h.habitaciones().get(0).id());
        assertEquals(4, h.habitaciones().get(0).unidadesLibres());
        verify(hotelesClient, never()).detalle(eq(lleno), any(), any(), anyInt());
    }
}
