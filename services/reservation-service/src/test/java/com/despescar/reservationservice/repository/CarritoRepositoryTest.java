package com.despescar.reservationservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import jakarta.persistence.PersistenceException;

import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.TramoPolitica;
import com.despescar.reservationservice.enums.EstadoItem;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class CarritoRepositoryTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private BookingRepository bookingRepository;

    static Reservation carrito(Long usuario, ReservationStatus estado, LocalDateTime limite) {
        return Reservation.builder()
                .creadorId(usuario)
                .cantidadPasajeros(0)
                .tipoPago(PaymentType.SINGLE_PAYMENT)
                .estado(estado)
                .limiteTiempo(limite)
                .build();
    }

    static EstadiaHotel estadia() {
        EstadiaHotel e = new EstadiaHotel();
        e.setHotelId(UUID.randomUUID());
        e.setHotelNombre("Sheraton Córdoba");
        e.setCiudad("Córdoba");
        e.setTipoHabitacionId(UUID.randomUUID());
        e.setTipoHabitacionNombre("Doble");
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setCantidadHabitaciones(2);
        e.setHuespedes(3);
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(new BigDecimal("580000.00"));
        e.setMoneda("ARS");
        e.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoPolitica(48, 100), new TramoPolitica(0, 0))));
        e.setHoraCheckIn(LocalTime.of(14, 0));
        e.setZonaHoraria("America/Argentina/Buenos_Aires");
        return e;
    }

    @Test
    void guardaElCarritoConSusEstadiasYLaPoliticaCopiada() {
        Reservation c = carrito(7L, ReservationStatus.INICIADA, AHORA.plusMinutes(15));
        EstadiaHotel e = estadia();
        e.setReservation(c);
        c.getEstadias().add(e);
        Long id = em.persistAndFlush(c).getId();
        em.clear();

        Reservation leido = bookingRepository.findById(id).orElseThrow();

        assertEquals(1, leido.getEstadias().size());
        EstadiaHotel guardada = leido.getEstadias().get(0);
        assertEquals(EstadoItem.ACTIVA, guardada.getEstado());
        assertEquals(PaymentStatus.PENDIENTE, guardada.getEstadoPago());
        assertEquals(2, guardada.getPoliticaCancelacion().size());
        assertEquals(48, guardada.getPoliticaCancelacion().get(0).getHorasAntes());
        assertEquals(0, new BigDecimal("580000.00").compareTo(guardada.getPrecioTotal()));
        assertTrue(leido.getFlightIds().isEmpty());
        assertTrue(leido.getDetalles().isEmpty());
    }

    @Test
    void quitarUnaEstadiaDeLaListaLaBorra() {
        Reservation c = carrito(7L, ReservationStatus.INICIADA, AHORA.plusMinutes(15));
        EstadiaHotel e = estadia();
        e.setReservation(c);
        c.getEstadias().add(e);
        Long id = em.persistAndFlush(c).getId();
        em.clear();

        Reservation leido = bookingRepository.findById(id).orElseThrow();
        leido.getEstadias().clear();
        em.flush();
        em.clear();

        assertTrue(bookingRepository.findById(id).orElseThrow().getEstadias().isEmpty());
    }

    @Test
    void encuentraElUltimoCarritoAbiertoDelUsuario() {
        em.persist(carrito(7L, ReservationStatus.CONFIRMADA, AHORA.plusMinutes(15)));
        Reservation abierto = em.persist(carrito(7L, ReservationStatus.INICIADA, AHORA.plusMinutes(15)));
        em.persist(carrito(8L, ReservationStatus.INICIADA, AHORA.plusMinutes(15)));
        em.flush();

        Reservation encontrado = bookingRepository.findFirstByCreadorIdAndEstadoInOrderByIdDesc(
                7L, List.of(ReservationStatus.INICIADA, ReservationStatus.PENDIENTE_PAGO)).orElseThrow();

        assertEquals(abierto.getId(), encontrado.getId());
    }

    @Test
    void listaLosVencidosQueSiguenAbiertos() {
        Reservation vencido = em.persist(carrito(7L, ReservationStatus.PENDIENTE_PAGO, AHORA.minusMinutes(1)));
        em.persist(carrito(8L, ReservationStatus.INICIADA, AHORA.plusMinutes(5)));
        em.persist(carrito(7L, ReservationStatus.CONFIRMADA, AHORA.minusMinutes(30)));
        em.flush();

        List<Reservation> vencidos = bookingRepository.findByEstadoInAndLimiteTiempoBefore(
                List.of(ReservationStatus.INICIADA, ReservationStatus.PENDIENTE_PAGO, ReservationStatus.ESPERANDO_PAGADORES),
                AHORA);

        assertEquals(List.of(vencido.getId()), vencidos.stream().map(Reservation::getId).toList());
    }

    @Test
    void dosCarritosAbiertosDelMismoUsuarioViolanElIndiceUnico() {
        em.persistAndFlush(carrito(7L, ReservationStatus.INICIADA, AHORA.plusMinutes(15)));

        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(carrito(7L, ReservationStatus.PENDIENTE_PAGO, AHORA.plusMinutes(15))));
    }

    @Test
    void unCarritoAbiertoPorUsuarioPeroVariosCerrados() {
        em.persist(carrito(7L, ReservationStatus.CONFIRMADA, AHORA.plusMinutes(15)));
        em.persist(carrito(7L, ReservationStatus.CANCELADA, AHORA.plusMinutes(15)));
        em.persist(carrito(7L, ReservationStatus.EXPIRADA, AHORA.plusMinutes(15)));
        em.persist(carrito(7L, ReservationStatus.INICIADA, AHORA.plusMinutes(15)));
        em.persist(carrito(8L, ReservationStatus.INICIADA, AHORA.plusMinutes(15)));
        em.flush();
    }

    @Test
    void alCerrarseUnCarritoElUsuarioPuedeAbrirOtro() {
        Reservation primero = em.persistAndFlush(carrito(7L, ReservationStatus.INICIADA, AHORA.plusMinutes(15)));
        assertEquals(7L, primero.getCarritoAbiertoDe());

        primero.setEstado(ReservationStatus.CANCELADA);
        em.flush();
        assertNull(primero.getCarritoAbiertoDe());

        Reservation segundo = em.persistAndFlush(carrito(7L, ReservationStatus.INICIADA, AHORA.plusMinutes(15)));
        assertEquals(7L, segundo.getCarritoAbiertoDe());
    }
}
