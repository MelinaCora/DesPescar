package com.despescar.reservationservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.despescar.reservationservice.entity.Seat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class SeatRepositoryOrdenTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private SeatRepository seatRepository;

    private Seat asiento(UUID vuelo, String numero) {
        Seat s = new Seat();
        s.setFlightId(vuelo);
        s.setNumberSeat(numero);
        s.setStatusSeat("RESERVADO_TEMPORAL");
        s.setBlockedByUserId(7L);
        s.setBloqueadoHasta(LocalDateTime.of(2026, 10, 5, 14, 0));
        return em.persist(s);
    }

    @Test
    void losVencidosSeDevuelvenOrdenadosPorVueloYNumeroParaBloquearEnElMismoOrdenQueElResto() {
        UUID vueloA = new UUID(0, 1);
        UUID vueloB = new UUID(0, 2);
        Seat b2 = asiento(vueloB, "2A");
        Seat a3 = asiento(vueloA, "3A");
        Seat b1 = asiento(vueloB, "1A");
        Seat a1 = asiento(vueloA, "1A");
        em.flush();

        List<UUID> ids = seatRepository.findIdsByStatusSeatAndBloqueadoHastaBefore(
                "RESERVADO_TEMPORAL", LocalDateTime.of(2026, 10, 5, 15, 0));

        assertEquals(new ArrayList<>(List.of(a1.getSeatUuid(), a3.getSeatUuid(), b1.getSeatUuid(), b2.getSeatUuid())), ids);
    }
}
