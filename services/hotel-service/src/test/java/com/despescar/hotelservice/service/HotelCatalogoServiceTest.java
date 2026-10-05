package com.despescar.hotelservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.despescar.hotelservice.dto.response.DestinoResponse;
import com.despescar.hotelservice.dto.response.HabitacionDetalleResponse;
import com.despescar.hotelservice.dto.response.HotelDetalleResponse;
import com.despescar.hotelservice.dto.response.HotelResumenResponse;
import com.despescar.hotelservice.entity.EstadoRetencion;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.Retencion;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.entity.TramoCancelacion;
import com.despescar.hotelservice.exception.HotelNotFoundException;
import com.despescar.hotelservice.exception.SolicitudInvalidaException;
import com.despescar.hotelservice.mapper.HotelMapper;
import com.despescar.hotelservice.repository.HotelRepository;
import com.despescar.hotelservice.repository.RetencionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HotelCatalogoServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-01T15:00:00Z"), ZONA);
    private static final LocalDate D10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D12 = LocalDate.of(2026, 11, 12);

    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private RetencionRepository retencionRepository;

    private HotelCatalogoService service;
    private Hotel cordoba;
    private Hotel madrid;

    @BeforeEach
    void setUp() {
        service = new HotelCatalogoService(hotelRepository, retencionRepository, new HotelMapper(), RELOJ);
        cordoba = hotel("Sheraton Córdoba", "Córdoba", "Argentina",
                habitacion("Doble", 2, "100000", 2), habitacion("Suite", 4, "300000", 1));
        madrid = hotel("Hilton Madrid", "Madrid", "España", habitacion("Doble", 2, "150000", 3));
    }

    private static TipoHabitacion habitacion(String nombre, int capacidad, String precio, int unidades) {
        TipoHabitacion t = new TipoHabitacion();
        t.setId(UUID.randomUUID());
        t.setNombre(nombre);
        t.setCapacidad(capacidad);
        t.setPrecioPorNoche(new BigDecimal(precio));
        t.setCantidadUnidades(unidades);
        return t;
    }

    private static Hotel hotel(String nombre, String ciudad, String pais, TipoHabitacion... habitaciones) {
        Hotel h = new Hotel();
        h.setId(UUID.randomUUID());
        h.setNombre(nombre);
        h.setCiudad(ciudad);
        h.setPais(pais);
        h.setDireccion("Calle 1");
        h.setEstrellas(4);
        h.setImagenes(new ArrayList<>(List.of("https://img/principal.jpg")));
        h.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoCancelacion(48, 100))));
        for (TipoHabitacion t : habitaciones) {
            h.agregarHabitacion(t);
        }
        return h;
    }

    private static Retencion confirmada(TipoHabitacion tipo, int cantidad) {
        Retencion r = new Retencion();
        r.setTipoHabitacionId(tipo.getId());
        r.setCheckIn(D10);
        r.setCheckOut(D12);
        r.setCantidad(cantidad);
        r.setEstado(EstadoRetencion.CONFIRMADA);
        return r;
    }

    @Test
    void buscaPorCiudadOPaisSinTildes() {
        when(hotelRepository.findByActivoTrue()).thenReturn(List.of(cordoba, madrid));

        assertEquals(List.of("Sheraton Córdoba"),
                service.buscar("cordoba", null, null, null).stream().map(HotelResumenResponse::nombre).toList());
        assertEquals(List.of("Hilton Madrid"),
                service.buscar("ESPANA", null, null, null).stream().map(HotelResumenResponse::nombre).toList());
        assertEquals(2, service.buscar("  ", null, null, null).size());
    }

    @Test
    void sinFechasDevuelvePrecioDesdeYSinDisponibilidad() {
        when(hotelRepository.findByActivoTrue()).thenReturn(List.of(cordoba));

        HotelResumenResponse resumen = service.buscar("Córdoba", null, null, 2).get(0);

        assertEquals(new BigDecimal("100000"), resumen.precioDesde());
        assertNull(resumen.disponible());
        assertNull(resumen.precioTotalDesde());
        assertEquals("https://img/principal.jpg", resumen.imagenPrincipal());
    }

    @Test
    void conFechasCalculaElTotalMasBaratoQueAlcanza() {
        when(hotelRepository.findByActivoTrue()).thenReturn(List.of(cordoba));
        TipoHabitacion doble = cordoba.getHabitaciones().get(0);
        when(retencionRepository.findActivasQueSolapan(anyCollection(), any(), any(), any()))
                .thenReturn(List.of(confirmada(doble, 1)));

        // 4 huéspedes: Doble necesita 2 unidades y queda 1 libre; Suite (4 pax) alcanza con 1.
        HotelResumenResponse resumen = service.buscar(null, D10, D12, 4).get(0);

        assertTrue(resumen.disponible());
        assertEquals(new BigDecimal("600000.00"), resumen.precioTotalDesde()); // Suite: 2 noches × 300000
    }

    @Test
    void sinLugarQuedaNoDisponible() {
        when(hotelRepository.findByActivoTrue()).thenReturn(List.of(madrid));
        TipoHabitacion doble = madrid.getHabitaciones().get(0);
        when(retencionRepository.findActivasQueSolapan(anyCollection(), any(), any(), any()))
                .thenReturn(List.of(confirmada(doble, 3)));

        HotelResumenResponse resumen = service.buscar(null, D10, D12, 2).get(0);

        assertFalse(resumen.disponible());
        assertNull(resumen.precioTotalDesde());
    }

    @Test
    void huespedesFueraDeRangoSeRechazan() {
        assertThrows(SolicitudInvalidaException.class, () -> service.buscar(null, null, null, 0));
        assertThrows(SolicitudInvalidaException.class, () -> service.buscar(null, null, null, 11));
    }

    @Test
    void destinosSinRepetirYOrdenados() {
        Hotel otroCordoba = hotel("Otro", "Córdoba", "Argentina", habitacion("Doble", 2, "1", 1));
        when(hotelRepository.findByActivoTrue()).thenReturn(List.of(madrid, cordoba, otroCordoba));

        assertEquals(List.of(new DestinoResponse("Córdoba", "Argentina"), new DestinoResponse("Madrid", "España")),
                service.destinos());
    }

    @Test
    void detalleConFechasCotizaCadaHabitacion() {
        when(hotelRepository.findByIdAndActivoTrue(cordoba.getId())).thenReturn(Optional.of(cordoba));
        when(retencionRepository.findActivasQueSolapan(anyCollection(), any(), any(), any())).thenReturn(List.of());

        HotelDetalleResponse detalle = service.detalle(cordoba.getId(), D10, D12, 2);

        assertEquals(2L, detalle.noches());
        HabitacionDetalleResponse doble = detalle.habitaciones().get(0);
        assertEquals(2, doble.unidadesLibres());
        assertEquals(1, doble.habitacionesNecesarias());
        assertEquals(new BigDecimal("200000.00"), doble.precioTotal());
        assertTrue(doble.disponible());
        assertEquals(48, detalle.politicaCancelacion().get(0).horasAntes());
    }

    @Test
    void detalleDeHotelInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(hotelRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.empty());

        assertThrows(HotelNotFoundException.class, () -> service.detalle(id, null, null, null));
    }
}
