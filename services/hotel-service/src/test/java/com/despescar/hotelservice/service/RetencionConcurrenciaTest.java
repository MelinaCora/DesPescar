package com.despescar.hotelservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.despescar.hotelservice.config.ClockConfig;
import com.despescar.hotelservice.dto.internal.RetencionRequest;
import com.despescar.hotelservice.entity.Hotel;
import com.despescar.hotelservice.entity.TipoHabitacion;
import com.despescar.hotelservice.entity.TramoCancelacion;
import com.despescar.hotelservice.exception.ConflictoException;
import com.despescar.hotelservice.repository.HotelRepository;
import com.despescar.hotelservice.repository.RetencionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Dos pedidos simultáneos por la última unidad: uno la obtiene y el otro recibe 409. */
@SpringBootTest
class RetencionConcurrenciaTest {

    private static final int HILOS = 8;
    private final java.util.concurrent.atomic.AtomicInteger conflictos = new java.util.concurrent.atomic.AtomicInteger();

    @Autowired
    private RetencionService service;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RetencionRepository retencionRepository;

    @AfterEach
    void limpiar() {
        retencionRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    @Test
    void dosPedidosSimultaneosNoSeQuedanConLaMismaUnidad() throws Exception {
        Hotel hotel = new Hotel();
        hotel.setNombre("Hotel Única");
        hotel.setCiudad("Córdoba");
        hotel.setPais("Argentina");
        hotel.setDireccion("Calle 1");
        hotel.setEstrellas(3);
        hotel.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoCancelacion(0, 0))));
        TipoHabitacion unica = new TipoHabitacion();
        unica.setNombre("Doble");
        unica.setCapacidad(2);
        unica.setPrecioPorNoche(new BigDecimal("100000.00"));
        unica.setCantidadUnidades(1);
        hotel.agregarHabitacion(unica);
        hotel = hotelRepository.save(hotel);
        UUID hotelId = hotel.getId();
        UUID tipoId = hotel.getHabitaciones().get(0).getId();
        LocalDate checkIn = LocalDate.now(ClockConfig.ZONA).plusDays(10);

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        List<Future<Boolean>> futuros = new ArrayList<>();
        for (long reserva = 1; reserva <= HILOS; reserva++) {
            futuros.add(pool.submit(intento(largada, hotelId, tipoId, checkIn, reserva)));
        }
        largada.countDown();
        int exitos = 0;
        for (Future<Boolean> f : futuros) {
            exitos += f.get(60, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();

        assertEquals(1, exitos);
        assertEquals(HILOS - 1, conflictos.get());
        assertEquals(1, retencionRepository.count());
    }

    private Callable<Boolean> intento(CountDownLatch largada, UUID hotelId, UUID tipoId, LocalDate checkIn, long reserva) {
        return () -> {
            largada.await();
            try {
                service.crear(new RetencionRequest(reserva, 7L, hotelId, tipoId, checkIn, checkIn.plusDays(2), 1, 2,
                        Instant.now().plus(15, ChronoUnit.MINUTES)));
                return true;
            } catch (ConflictoException ex) {
                if ("SIN_DISPONIBILIDAD".equals(ex.getCodigo())) {
                    conflictos.incrementAndGet();
                }
                return false;
            }
        };
    }
}
