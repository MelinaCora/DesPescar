package com.despescar.reservationservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class GrupoPagoRepositoryTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private GrupoPagoRepository repository;

    private Reservation reserva(Long creador, ReservationStatus estado) {
        return em.persist(Reservation.builder().creadorId(creador).cantidadPasajeros(0)
                .tipoPago(PaymentType.SPLIT_PAYMENT).estado(estado).limiteTiempo(AHORA.plusHours(24)).build());
    }

    private GrupoPago grupo(Reservation reserva, String token, EstadoGrupo estado, LocalDateTime vence) {
        GrupoPago g = new GrupoPago();
        g.setReservation(reserva);
        g.setOrganizadorId(reserva.getCreadorId());
        g.setTokenEnlace(token);
        g.setEstado(estado);
        g.setVenceEn(vence);
        g.setCreadoEn(AHORA);
        g.setActualizadoEn(AHORA);
        ParteGrupo organizador = ParteGrupo.libre(1, new BigDecimal("500.00"));
        organizador.tomar(reserva.getCreadorId(), null);
        g.agregarParte(organizador);
        g.agregarParte(ParteGrupo.libre(2, new BigDecimal("500.00")));
        return g;
    }

    private static String token(char c) {
        return String.valueOf(c).repeat(43);
    }

    @Test
    void guardaElGrupoConSusPartesOrdenadasYLoEncuentraPorTokenYReserva() {
        Reservation r = reserva(7L, ReservationStatus.ESPERANDO_PAGADORES);
        em.persistAndFlush(grupo(r, token('a'), EstadoGrupo.ABIERTO, AHORA.plusHours(24)));
        em.clear();

        GrupoPago porToken = repository.findByTokenEnlace(token('a')).orElseThrow();
        GrupoPago porReserva = repository.findByReservation_Id(r.getId()).orElseThrow();

        assertEquals(porToken.getId(), porReserva.getId());
        assertEquals(List.of(1, 2), porToken.getPartes().stream().map(ParteGrupo::getNumero).toList());
        assertEquals(EstadoParte.TOMADA, porToken.getPartes().get(0).getEstado());
        assertEquals(7L, porToken.getPartes().get(0).getUsuarioId());
        assertEquals(EstadoParte.LIBRE, porToken.getPartes().get(1).getEstado());
        assertTrue(repository.findByTokenForUpdate(token('a')).isPresent());
        assertTrue(repository.findByReservaIdForUpdate(r.getId()).isPresent());
    }

    @Test
    void unaReservaTieneUnSoloGrupo() {
        Reservation r = reserva(7L, ReservationStatus.ESPERANDO_PAGADORES);
        em.persistAndFlush(grupo(r, token('a'), EstadoGrupo.ABIERTO, AHORA.plusHours(24)));

        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(grupo(r, token('b'), EstadoGrupo.ABIERTO, AHORA.plusHours(24))));
    }

    @Test
    void elTokenEsUnico() {
        em.persistAndFlush(grupo(reserva(7L, ReservationStatus.ESPERANDO_PAGADORES), token('a'), EstadoGrupo.ABIERTO, AHORA));

        assertThrows(PersistenceException.class, () -> em.persistAndFlush(
                grupo(reserva(8L, ReservationStatus.ESPERANDO_PAGADORES), token('a'), EstadoGrupo.ABIERTO, AHORA)));
    }

    @Test
    void unUsuarioNoPuedeTenerDosPartesDelMismoGrupo() {
        GrupoPago g = em.persistAndFlush(grupo(reserva(7L, ReservationStatus.ESPERANDO_PAGADORES), token('a'),
                EstadoGrupo.ABIERTO, AHORA));

        g.getPartes().get(1).tomar(7L, "Otra vez yo");

        assertThrows(PersistenceException.class, () -> em.flush());
    }

    @Test
    void variasPartesLibresNoChocanEntreSi() {
        GrupoPago g = grupo(reserva(7L, ReservationStatus.ESPERANDO_PAGADORES), token('a'), EstadoGrupo.ABIERTO, AHORA);
        g.agregarParte(ParteGrupo.libre(3, new BigDecimal("500.00")));

        em.persistAndFlush(g);
    }

    @Test
    void listaLosVencidosLosQueEsperanReembolsoYLosDeUnUsuario() {
        GrupoPago vencido = em.persist(grupo(reserva(7L, ReservationStatus.ESPERANDO_PAGADORES), token('a'),
                EstadoGrupo.ABIERTO, AHORA.minusMinutes(1)));
        GrupoPago vigente = em.persist(grupo(reserva(8L, ReservationStatus.ESPERANDO_PAGADORES), token('b'),
                EstadoGrupo.ABIERTO, AHORA.plusHours(1)));
        GrupoPago cancelado = grupo(reserva(9L, ReservationStatus.CANCELADA), token('c'), EstadoGrupo.CANCELADO, AHORA);
        cancelado.setReembolsosPendientes(true);
        em.persist(cancelado);
        vigente.getPartes().get(1).tomar(9L, "Juli");
        em.flush();

        assertEquals(List.of(vencido.getId()), repository.idsVencidos(EstadoGrupo.ABIERTO, AHORA));
        assertEquals(List.of(cancelado.getId()), repository.idsConReembolsosPendientes());
        assertEquals(List.of(vigente.getId()),
                repository.gruposConParteDe(9L, List.of(EstadoGrupo.ABIERTO, EstadoGrupo.COMPLETO))
                        .stream().map(GrupoPago::getId).toList());
        assertEquals(2, repository.idsSinCambiosDesde(EstadoGrupo.ABIERTO, AHORA).size());
    }

    @Test
    void laReservaEsperandoPagadoresSigueOcupandoElLugarDeCarritoAbierto() {
        Reservation r = reserva(7L, ReservationStatus.ESPERANDO_PAGADORES);
        em.flush();

        assertEquals(7L, r.getCarritoAbiertoDe());
        assertThrows(PersistenceException.class, () -> {
            reserva(7L, ReservationStatus.INICIADA);
            em.flush();
        });
    }
}
