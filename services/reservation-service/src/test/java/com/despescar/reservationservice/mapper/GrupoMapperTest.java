package com.despescar.reservationservice.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.despescar.reservationservice.dto.grupo.GrupoResponse;
import com.despescar.reservationservice.dto.grupo.GrupoResumenResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.TramoPolitica;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoItem;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GrupoMapperTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final UUID IDA = UUID.randomUUID();

    private final GrupoMapper mapper = new GrupoMapper(RELOJ);
    private GrupoPago grupo;

    @BeforeEach
    void setUp() {
        Reservation r = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(1)
                .tipoPago(PaymentType.SPLIT_PAYMENT).estado(ReservationStatus.ESPERANDO_PAGADORES)
                .limiteTiempo(AHORA.plusHours(2)).flightIds(new ArrayList<>(List.of(IDA)))
                .precioVueloPorPasajero(new BigDecimal("240000.00")).tarifasVuelo("Light")
                .salidaVuelo(LocalDateTime.of(2026, 10, 19, 8, 0)).build();
        r.getDetalles().add(ReservationDetail.builder().reservation(r).outboundSeatNumber("1A")
                .passengerName("Ana Pérez").passengerDni("30111222").payerUserId(7L)
                .priceCharged(new BigDecimal("240000.00")).paymentStatus(PaymentStatus.PENDIENTE).build());
        EstadiaHotel e = new EstadiaHotel();
        e.setReservation(r);
        e.setHotelNombre("Sheraton Córdoba");
        e.setCiudad("Córdoba");
        e.setTipoHabitacionNombre("Doble");
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setCantidadHabitaciones(1);
        e.setHuespedes(2);
        e.setPrecioTotal(new BigDecimal("60000.00"));
        e.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoPolitica(48, 100))));
        e.setTitularNombre("Ana Pérez");
        e.setTitularDni("30111222");
        e.setTitularTelefono("1155555555");
        r.getEstadias().add(e);
        EstadiaHotel cancelada = new EstadiaHotel();
        cancelada.setReservation(r);
        cancelada.setEstado(EstadoItem.CANCELADA);
        cancelada.setHotelNombre("Otro");
        cancelada.setCheckIn(LocalDate.of(2026, 11, 10));
        cancelada.setCheckOut(LocalDate.of(2026, 11, 11));
        cancelada.setPrecioTotal(new BigDecimal("1.00"));
        r.getEstadias().add(cancelada);

        grupo = new GrupoPago();
        grupo.setId(30L);
        grupo.setReservation(r);
        grupo.setOrganizadorId(7L);
        grupo.setTokenEnlace("t".repeat(43));
        grupo.setEstado(EstadoGrupo.ABIERTO);
        grupo.setVenceEn(AHORA.plusHours(2));
        ParteGrupo uno = ParteGrupo.libre(1, new BigDecimal("150000.00"));
        uno.tomar(7L, null);
        uno.pagar("MOCK-1", AHORA);
        ParteGrupo dos = ParteGrupo.libre(2, new BigDecimal("150000.00"));
        dos.tomar(9L, "Juli");
        grupo.agregarParte(uno);
        grupo.agregarParte(dos);
    }

    @Test
    void unAmigoVeSuParteElViajeYElEnlaceSinDatosPersonales() throws Exception {
        GrupoResponse r = mapper.toResponse(grupo, 9L);

        assertEquals(12L, r.reservaId());
        assertEquals(new BigDecimal("300000.00"), r.montoTotal());
        assertEquals(new BigDecimal("150000.00"), r.montoPagado());
        assertEquals(1, r.partesPagadas());
        assertEquals(2, r.miParte());
        assertFalse(r.soyOrganizador());
        assertFalse(r.puedeEditarMontos());
        assertEquals("t".repeat(43), r.enlaceToken());
        assertEquals(7200, r.segundosRestantes());
        assertTrue(r.partes().get(0).esOrganizador());
        assertFalse(r.partes().get(0).esMia());
        assertTrue(r.partes().get(1).esMia());
        assertEquals("Juli", r.partes().get(1).apodo());
        assertEquals(List.of(IDA), r.viaje().vuelo().flightIds());
        assertEquals(1, r.viaje().estadias().size());
        assertEquals(2, r.viaje().estadias().get(0).noches());
        assertEquals(48, r.viaje().estadias().get(0).politicaCancelacion().get(0).getHorasAntes());

        String json = JsonMapper.builder().build().writeValueAsString(r);
        for (String privado : List.of("Ana Pérez", "30111222", "1155555555", "1A", "creadorId", "titular", "usuarioId")) {
            assertFalse(json.contains(privado), "la respuesta no debe incluir " + privado);
        }
    }

    @Test
    void quienNoTieneParteNoRecibeElToken() {
        GrupoResponse r = mapper.toResponse(grupo, 11L);

        assertNull(r.miParte());
        assertNull(r.enlaceToken());
        assertFalse(r.partes().get(1).esMia());
    }

    @Test
    void elOrganizadorPuedeEditarSoloSiNadiePago() {
        assertFalse(mapper.toResponse(grupo, 7L).puedeEditarMontos());

        grupo.getPartes().get(0).tomar(7L, null);

        GrupoResponse r = mapper.toResponse(grupo, 7L);
        assertTrue(r.soyOrganizador());
        assertTrue(r.puedeEditarMontos());
    }

    @Test
    void unGrupoCerradoNoTieneTiempoNiEdicion() {
        grupo.getPartes().get(0).tomar(7L, null);
        grupo.setEstado(EstadoGrupo.VENCIDO);
        grupo.setMotivoCierre("PAGO_EN_GRUPO_VENCIDO");

        GrupoResponse r = mapper.toResponse(grupo, 7L);

        assertEquals(0, r.segundosRestantes());
        assertFalse(r.puedeEditarMontos());
        assertEquals("PAGO_EN_GRUPO_VENCIDO", r.motivoCierre());
    }

    @Test
    void elResumenTraeLaParteDelUsuarioYElDestino() {
        GrupoResumenResponse r = mapper.resumen(grupo, 9L);

        assertEquals(12L, r.reservaId());
        assertEquals(2, r.miParte());
        assertEquals(new BigDecimal("150000.00"), r.monto());
        assertEquals("Córdoba", r.destino());
        assertFalse(r.soyOrganizador());
        assertEquals("t".repeat(43), r.enlaceToken());
    }
}
