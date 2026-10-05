package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.dto.grupo.EditarPartesRequest;
import com.despescar.reservationservice.dto.grupo.GrupoResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.GrupoMapper;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class GrupoPagoServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final String TOKEN = "k".repeat(43);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private GrupoPagoRepository grupoRepository;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private GrupoCierre cierre;
    @Mock
    private PlatformTransactionManager transactionManager;

    private GrupoPagoService service;
    private Reservation reserva;
    private GrupoPago grupo;

    @BeforeEach
    void setUp() {
        service = new GrupoPagoService(bookingRepository, grupoRepository,
                new CarritoSoporte(bookingRepository, RELOJ, transactionManager, inventario), inventario, new GrupoMapper(RELOJ),
                new TokenEnlace(), new TransactionTemplate(transactionManager), cierre);
        reserva = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SPLIT_PAYMENT).estado(ReservationStatus.ESPERANDO_PAGADORES)
                .limiteTiempo(AHORA.plusHours(20)).build();
        EstadiaHotel e = new EstadiaHotel();
        e.setReservation(reserva);
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(new BigDecimal("900000.00"));
        reserva.getEstadias().add(e);
        grupo = new GrupoPago();
        grupo.setId(30L);
        grupo.setReservation(reserva);
        grupo.setOrganizadorId(7L);
        grupo.setTokenEnlace(TOKEN);
        grupo.setEstado(EstadoGrupo.ABIERTO);
        grupo.setVenceEn(AHORA.plusHours(20));
        for (int i = 1; i <= 3; i++) {
            ParteGrupo p = ParteGrupo.libre(i, new BigDecimal("300000.00"));
            if (i == 1) {
                p.tomar(7L, null);
            }
            grupo.agregarParte(p);
        }
        lenient().when(bookingRepository.findById(12L)).thenReturn(Optional.of(reserva));
        lenient().when(grupoRepository.findByTokenForUpdate(TOKEN)).thenReturn(Optional.of(grupo));
        lenient().when(grupoRepository.findByReservaIdForUpdate(12L)).thenReturn(Optional.of(grupo));
        lenient().when(grupoRepository.findByReservation_Id(12L)).thenReturn(Optional.of(grupo));
        lenient().when(grupoRepository.findById(30L)).thenReturn(Optional.of(grupo));
        lenient().when(grupoRepository.saveAndFlush(any(GrupoPago.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- unirse ----------

    @Test
    void unAmigoTomaLaPrimeraParteLibreConSuApodo() {
        GrupoResponse r = service.unirse(TOKEN, "  Juli   P. ", 9L);

        assertEquals(2, r.miParte());
        assertEquals(EstadoParte.TOMADA, grupo.getPartes().get(1).getEstado());
        assertEquals(9L, grupo.getPartes().get(1).getUsuarioId());
        assertEquals("Juli P.", grupo.getPartes().get(1).getApodo());
        assertEquals(TOKEN, r.enlaceToken());
        assertEquals(AHORA, grupo.getActualizadoEn());
    }

    @Test
    void sumarseDosVecesDevuelveLaMismaParte() {
        service.unirse(TOKEN, null, 9L);

        GrupoResponse r = service.unirse(TOKEN, "Otro apodo", 9L);

        assertEquals(2, r.miParte());
        assertNull(grupo.getPartes().get(1).getApodo());
        assertEquals(EstadoParte.LIBRE, grupo.getPartes().get(2).getEstado());
    }

    @Test
    void elOrganizadorYaTieneSuParte() {
        assertEquals(1, service.unirse(TOKEN, null, 7L).miParte());
        assertEquals(EstadoParte.LIBRE, grupo.getPartes().get(1).getEstado());
    }

    @Test
    void sinPartesLibresResponde409() {
        service.unirse(TOKEN, null, 9L);
        service.unirse(TOKEN, null, 10L);

        BookingException ex = assertThrows(BookingException.class, () -> service.unirse(TOKEN, null, 11L));

        assertEquals("GRUPO_COMPLETO", ex.getCodigo());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void unEnlaceMalFormadoOInexistenteResponde404Igual() {
        when(grupoRepository.findByTokenForUpdate("z".repeat(43))).thenReturn(Optional.empty());

        BookingException malFormado = assertThrows(BookingException.class, () -> service.unirse("123", null, 9L));
        BookingException inexistente = assertThrows(BookingException.class, () -> service.unirse("z".repeat(43), null, 9L));

        assertEquals("GRUPO_NO_ENCONTRADO", malFormado.getCodigo());
        assertEquals(malFormado.getMessage(), inexistente.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, inexistente.getStatus());
        verify(grupoRepository, never()).findByTokenForUpdate("123");
    }

    @Test
    void unGrupoVencidoOCerradoNoSumaGente() {
        grupo.setVenceEn(AHORA);
        assertEquals("ENLACE_VENCIDO", assertThrows(BookingException.class, () -> service.unirse(TOKEN, null, 9L)).getCodigo());

        grupo.setVenceEn(AHORA.plusHours(1));
        grupo.setEstado(EstadoGrupo.CANCELADO);
        BookingException ex = assertThrows(BookingException.class, () -> service.unirse(TOKEN, null, 9L));
        assertEquals("ENLACE_VENCIDO", ex.getCodigo());
        assertEquals(HttpStatus.GONE, ex.getStatus());
    }

    @Test
    void consultarSinParteUnGrupoCerradoResponde410PeroConParteSeVe() {
        service.unirse(TOKEN, null, 9L);
        grupo.setEstado(EstadoGrupo.VENCIDO);
        when(grupoRepository.findByTokenEnlace(TOKEN)).thenReturn(Optional.of(grupo));

        assertEquals(EstadoGrupo.VENCIDO, service.consultar(TOKEN, 9L).estado());
        assertEquals("ENLACE_VENCIDO", assertThrows(BookingException.class, () -> service.consultar(TOKEN, 11L)).getCodigo());
    }

    @Test
    void laParticipacionEsSoloParaQuienTieneParte() {
        assertEquals(1, service.participacion(12L, 7L).miParte());
        assertEquals("GRUPO_NO_ENCONTRADO",
                assertThrows(BookingException.class, () -> service.participacion(12L, 11L)).getCodigo());
    }

    // ---------- editar ----------

    @Test
    void elOrganizadorEditaLosMontosSiSumanElTotal() {
        GrupoResponse r = service.editarPartes(12L, new EditarPartesRequest(null,
                List.of(new BigDecimal("400000"), new BigDecimal("250000"), new BigDecimal("250000"))), 7L);

        assertEquals(new BigDecimal("400000.00"), r.partes().get(0).monto());
        assertEquals(new BigDecimal("250000.00"), grupo.getPartes().get(2).getMonto());
    }

    @Test
    void sinAmigosSePuedeCambiarLaCantidadDePartes() {
        GrupoResponse mas = service.editarPartes(12L, new EditarPartesRequest(4, null), 7L);
        assertEquals(4, mas.cantidadPartes());
        assertEquals(new BigDecimal("225000.00"), grupo.getPartes().get(3).getMonto());
        assertEquals(EstadoParte.LIBRE, grupo.getPartes().get(3).getEstado());

        GrupoResponse menos = service.editarPartes(12L, new EditarPartesRequest(null,
                List.of(new BigDecimal("500000"), new BigDecimal("400000"))), 7L);
        assertEquals(2, menos.cantidadPartes());
        assertEquals(List.of(1, 2), grupo.getPartes().stream().map(ParteGrupo::getNumero).toList());
        assertEquals(7L, grupo.getPartes().get(0).getUsuarioId());
    }

    @Test
    void conAmigosSumadosNoSeCambiaLaCantidadPeroSiLosMontos() {
        grupo.getPartes().get(1).tomar(9L, "Juli");

        assertEquals("GRUPO_CON_INVITADOS", assertThrows(BookingException.class,
                () -> service.editarPartes(12L, new EditarPartesRequest(4, null), 7L)).getCodigo());
        assertEquals(new BigDecimal("300000.00"), service.editarPartes(12L, new EditarPartesRequest(3, null), 7L)
                .partes().get(1).monto());
    }

    @Test
    void conAlgunaPartePagadaNoSeEdita() {
        grupo.getPartes().get(0).pagar("MOCK-1", AHORA);

        assertEquals("GRUPO_CON_PAGOS", assertThrows(BookingException.class,
                () -> service.editarPartes(12L, new EditarPartesRequest(3, null), 7L)).getCodigo());
    }

    @Test
    void montosQueNoSumanSeRechazanSinTocarElGrupo() {
        assertEquals("MONTOS_NO_SUMAN_TOTAL", assertThrows(BookingException.class,
                () -> service.editarPartes(12L, new EditarPartesRequest(null,
                        List.of(new BigDecimal("400000"), new BigDecimal("250000"), new BigDecimal("249999.99"))), 7L))
                .getCodigo());
        assertEquals(new BigDecimal("300000.00"), grupo.getPartes().get(0).getMonto());
    }

    @Test
    void hayQueMandarCantidadOMontosPeroNoAmbos() {
        assertEquals("VALIDACION", assertThrows(BookingException.class,
                () -> service.editarPartes(12L, new EditarPartesRequest(null, null), 7L)).getCodigo());
        assertEquals("VALIDACION", assertThrows(BookingException.class,
                () -> service.editarPartes(12L, new EditarPartesRequest(3, List.of(BigDecimal.ONE, BigDecimal.ONE)), 7L))
                .getCodigo());
    }

    @Test
    void soloElOrganizadorEditaLibera() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BookingException.class,
                () -> service.editarPartes(12L, new EditarPartesRequest(3, null), 9L)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BookingException.class,
                () -> service.liberarParte(12L, 2, 9L)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BookingException.class,
                () -> service.cancelar(12L, 9L)).getStatus());
        verify(cierre, never()).cerrar(anyLong(), any(), any(), anyBoolean());
    }

    @Test
    void unGrupoVencidoOCerradoNoSeEdita() {
        grupo.setVenceEn(AHORA.minusSeconds(1));
        assertEquals("GRUPO_VENCIDO", assertThrows(BookingException.class,
                () -> service.editarPartes(12L, new EditarPartesRequest(3, null), 7L)).getCodigo());

        grupo.setEstado(EstadoGrupo.CANCELADO);
        assertEquals("GRUPO_CERRADO", assertThrows(BookingException.class,
                () -> service.editarPartes(12L, new EditarPartesRequest(3, null), 7L)).getCodigo());
    }

    // ---------- liberar ----------

    @Test
    void elOrganizadorLiberaLaParteDeUnAmigoQueNoPago() {
        grupo.getPartes().get(1).tomar(9L, "Juli");

        GrupoResponse r = service.liberarParte(12L, 2, 7L);

        assertEquals(EstadoParte.LIBRE, r.partes().get(1).estado());
        assertNull(grupo.getPartes().get(1).getUsuarioId());
        assertNull(grupo.getPartes().get(1).getApodo());
    }

    @Test
    void noSeLiberaAlOrganizadorNiUnaParteInexistenteNiUnaPagada() {
        grupo.getPartes().get(1).tomar(9L, "Juli");
        grupo.getPartes().get(1).pagar("MOCK-2", AHORA);

        assertEquals("ORGANIZADOR_NO_SE_QUITA",
                assertThrows(BookingException.class, () -> service.liberarParte(12L, 1, 7L)).getCodigo());
        assertEquals("PARTE_NO_ENCONTRADA",
                assertThrows(BookingException.class, () -> service.liberarParte(12L, 9, 7L)).getCodigo());
        assertEquals("PARTE_PAGADA",
                assertThrows(BookingException.class, () -> service.liberarParte(12L, 2, 7L)).getCodigo());
    }

    @Test
    void liberarUnaParteLibreEsIdempotente() {
        assertEquals(EstadoParte.LIBRE, service.liberarParte(12L, 3, 7L).partes().get(2).estado());
    }

    // ---------- cancelar ----------

    @Test
    void cancelarUnGrupoAbiertoLoCierraConSuMotivo() {
        when(cierre.cerrar(30L, EstadoGrupo.CANCELADO, GrupoCierre.MOTIVO_CANCELADO, false)).thenAnswer(inv -> {
            grupo.setEstado(EstadoGrupo.CANCELADO);
            grupo.setMotivoCierre(GrupoCierre.MOTIVO_CANCELADO);
            return true;
        });

        GrupoResponse r = service.cancelar(12L, 7L);

        assertEquals(EstadoGrupo.CANCELADO, r.estado());
        assertEquals("GRUPO_CANCELADO", r.motivoCierre());
    }

    @Test
    void cancelarUnGrupoQueYaTerminoEsIdempotente() {
        grupo.setEstado(EstadoGrupo.VENCIDO);

        assertEquals(EstadoGrupo.VENCIDO, service.cancelar(12L, 7L).estado());
        verify(cierre, never()).cerrar(anyLong(), any(), any(), anyBoolean());
    }

    @Test
    void noSeCancelaMientrasSeConfirmaNiUnaVezConfirmado() {
        grupo.setEstado(EstadoGrupo.COMPLETO);
        assertEquals("GRUPO_CONFIRMANDO", assertThrows(BookingException.class, () -> service.cancelar(12L, 7L)).getCodigo());

        grupo.setEstado(EstadoGrupo.CONFIRMADO);
        assertEquals("USAR_CANCELACION_POR_ITEM", assertThrows(BookingException.class, () -> service.cancelar(12L, 7L)).getCodigo());
    }
}
