package com.despescar.hotelservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.hotelservice.entity.EstadoRetencion;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.Retencion;
import com.despescar.hotelservice.entity.Servicio;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.entity.TramoCancelacion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class CatalogoRepositoryTest {

    private static final LocalDate D10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D13 = LocalDate.of(2026, 11, 13);
    private static final Instant AHORA = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired
    private TestEntityManager em;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RetencionRepository retencionRepository;

    private Hotel hotelCompleto(boolean activo) {
        Hotel hotel = new Hotel();
        hotel.setNombre("Llao Llao");
        hotel.setCiudad("San Carlos de Bariloche");
        hotel.setPais("Argentina");
        hotel.setDireccion("Av. Bustillo km 25");
        hotel.setEstrellas(5);
        hotel.setActivo(activo);
        hotel.setImagenes(new java.util.ArrayList<>(List.of("https://img/1.jpg", "https://img/2.jpg")));
        hotel.setServicios(new java.util.HashSet<>(Set.of(Servicio.WIFI, Servicio.SPA)));
        hotel.setPoliticaCancelacion(new java.util.ArrayList<>(List.of(new TramoCancelacion(72, 100), new TramoCancelacion(0, 0))));
        TipoHabitacion doble = new TipoHabitacion();
        doble.setNombre("Doble");
        doble.setCapacidad(2);
        doble.setPrecioPorNoche(new BigDecimal("250000.00"));
        doble.setCantidadUnidades(4);
        hotel.agregarHabitacion(doble);
        return hotel;
    }

    private Retencion retencion(UUID tipoId, LocalDate in, LocalDate out, EstadoRetencion estado, Instant expira) {
        Retencion r = new Retencion();
        r.setReservaId(1L);
        r.setUsuarioId(7L);
        r.setTipoHabitacionId(tipoId);
        r.setCheckIn(in);
        r.setCheckOut(out);
        r.setCantidad(1);
        r.setHuespedes(2);
        r.setEstado(estado);
        r.setExpiraEn(expira);
        return em.persist(r);
    }

    @Test
    void guardaElHotelConSusColeccionesYSoloListaActivos() {
        Hotel activo = hotelRepository.save(hotelCompleto(true));
        hotelRepository.save(hotelCompleto(false));
        em.flush();
        em.clear();

        List<Hotel> activos = hotelRepository.findByActivoTrue();
        assertEquals(1, activos.size());
        Hotel leido = hotelRepository.findByIdAndActivoTrue(activo.getId()).orElseThrow();
        assertEquals(List.of("https://img/1.jpg", "https://img/2.jpg"), leido.getImagenes());
        assertEquals(Set.of(Servicio.WIFI, Servicio.SPA), leido.getServicios());
        assertEquals(72, leido.getPoliticaCancelacion().get(0).getHorasAntes());
        assertEquals(1, leido.getHabitaciones().size());
        assertEquals(Hotel.HORA_CHECK_IN_POR_DEFECTO, leido.getHoraCheckIn());
        assertEquals(Hotel.ZONA_POR_DEFECTO, leido.getZonaHoraria());
    }

    @Test
    void soloDevuelveRetencionesActivasQueSolapan() {
        Hotel hotel = hotelRepository.save(hotelCompleto(true));
        UUID tipo = hotel.getHabitaciones().get(0).getId();
        Instant futuro = AHORA.plusSeconds(600);
        Instant pasado = AHORA.minusSeconds(600);

        retencion(tipo, D10, D13, EstadoRetencion.CONFIRMADA, pasado);           // cuenta (confirmada)
        retencion(tipo, D10.plusDays(1), D13, EstadoRetencion.RETENIDA, futuro);  // cuenta (vigente)
        retencion(tipo, D10, D13, EstadoRetencion.RETENIDA, pasado);             // no: vencida
        retencion(tipo, D10, D13, EstadoRetencion.LIBERADA, futuro);             // no: liberada
        retencion(tipo, D13, D13.plusDays(2), EstadoRetencion.CONFIRMADA, futuro); // no: empieza el día del checkOut
        retencion(tipo, D10.minusDays(3), D10, EstadoRetencion.CONFIRMADA, futuro); // no: termina el día del checkIn
        em.flush();

        List<Retencion> activas = retencionRepository.findActivasQueSolapan(List.of(tipo), D10, D13, AHORA);

        assertEquals(2, activas.size());
        assertTrue(activas.stream().allMatch(r -> r.getEstado() != EstadoRetencion.LIBERADA));
    }
}
