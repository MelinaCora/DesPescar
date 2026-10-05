package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.dto.grupo.PagoParteRequest;
import com.despescar.reservationservice.dto.grupo.ParteInternaResponse;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.EstadoParte;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PagoParteServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);
    private static final BigDecimal PARTE = new BigDecimal("300000.00");

    @Mock
    private GrupoPagoRepository grupoRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingService bookingService;
    @Mock
    private GrupoCierre cierre;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PagoParteService service;
    private GrupoPago grupo;

    @BeforeEach
    void setUp() {
        service = new PagoParteService(grupoRepository, bookingService, cierre,
                new CarritoSoporte(bookingRepository, RELOJ, transactionManager, inventario), new TransactionTemplate(transactionManager));
        Reservation reserva = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(0)
                .tipoPago(PaymentType.SPLIT_PAYMENT).estado(ReservationStatus.ESPERANDO_PAGADORES)
                .limiteTiempo(AHORA.plusHours(5)).build();
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
        grupo.setEstado(EstadoGrupo.ABIERTO);
        grupo.setVenceEn(AHORA.plusHours(5));
        long[] usuarios = {7L, 9L, 10L};
        for (int i = 0; i < 3; i++) {
            ParteGrupo p = ParteGrupo.libre(i + 1, PARTE);
            p.tomar(usuarios[i], null);
            grupo.agregarParte(p);
        }
        lenient().when(grupoRepository.findByReservaIdForUpdate(12L)).thenReturn(Optional.of(grupo));
        lenient().when(grupoRepository.findByReservation_Id(12L)).thenReturn(Optional.of(grupo));
        lenient().when(grupoRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(grupo));
    }

    private static PagoParteRequest pago(Long pagador, String token, String monto) {
        return new PagoParteRequest(pagador, token, new BigDecimal(monto));
    }

    @Test
    void laParteInternaTraeDuenoMontoYEstados() {
        ParteInternaResponse p = service.parte(12L, 2);

        assertEquals(9L, p.usuarioId());
        assertEquals(PARTE, p.monto());
        assertEquals("ARS", p.moneda());
        assertEquals(EstadoParte.TOMADA, p.estadoParte());
        assertEquals(EstadoGrupo.ABIERTO, p.estadoGrupo());
        assertEquals(5 * 3600, p.segundosRestantes());
    }

    @Test
    void unaParteInexistenteResponde404() {
        BookingException ex = assertThrows(BookingException.class, () -> service.parte(12L, 7));

        assertEquals("PARTE_NO_ENCONTRADA", ex.getCodigo());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void pagarUnaParteQueNoEsLaUltimaRespondePartePagada() {
        ConfirmacionPagoResponse r = service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000"));

        assertEquals("PARTE_PAGADA", r.estado());
        assertNull(r.motivo());
        assertEquals("Parte pagada. Faltan 2 de 3.", r.mensaje());
        assertEquals(EstadoParte.PAGADA, grupo.getPartes().get(1).getEstado());
        assertEquals("MOCK-2", grupo.getPartes().get(1).getTokenPago());
        assertEquals(AHORA, grupo.getPartes().get(1).getPagadaEn());
        assertEquals(EstadoGrupo.ABIERTO, grupo.getEstado());
        verifyNoInteractions(bookingService);
    }

    @Test
    void laUltimaParteConfirmaLaReservaFueraDelLockYCierraElGrupo() {
        grupo.getPartes().get(0).pagar("MOCK-1", AHORA);
        grupo.getPartes().get(1).pagar("MOCK-2", AHORA);
        when(bookingService.confirmarReservaPagada(12L, "GRUPO-30")).thenReturn(ConfirmacionPagoResponse.confirmada());

        ConfirmacionPagoResponse r = service.confirmarPago(12L, 3, pago(10L, "MOCK-3", "300000.00"));

        assertEquals("CONFIRMADA", r.estado());
        assertEquals(EstadoGrupo.CONFIRMADO, grupo.getEstado());
        // registrar (tx) → confirmar la reserva sin transacción abierta → marcar CONFIRMADO (otra tx)
        InOrder orden = inOrder(transactionManager, bookingService);
        orden.verify(transactionManager).commit(any());
        orden.verify(bookingService).confirmarReservaPagada(12L, "GRUPO-30");
        orden.verify(transactionManager).getTransaction(any());
    }

    @Test
    void siLaReservaNoTieneLugarElGrupoSeCancelaYSeReembolsa() {
        grupo.getPartes().get(0).pagar("MOCK-1", AHORA);
        grupo.getPartes().get(1).pagar("MOCK-2", AHORA);
        when(bookingService.confirmarReservaPagada(12L, "GRUPO-30"))
                .thenReturn(ConfirmacionPagoResponse.cancelada("SIN_DISPONIBILIDAD", "Ya no hay lugar para todo el carrito."));

        ConfirmacionPagoResponse r = service.confirmarPago(12L, 3, pago(10L, "MOCK-3", "300000.00"));

        assertEquals("CANCELADA", r.estado());
        assertEquals("SIN_DISPONIBILIDAD", r.motivo());
        verify(cierre).marcarCanceladoTrasConfirmar(30L, "SIN_DISPONIBILIDAD");
    }

    @Test
    void unaCaidaDelHotelAlConfirmarSePropagaYElGrupoQuedaCompleto() {
        grupo.getPartes().get(0).pagar("MOCK-1", AHORA);
        grupo.getPartes().get(1).pagar("MOCK-2", AHORA);
        when(bookingService.confirmarReservaPagada(12L, "GRUPO-30"))
                .thenThrow(new BookingException("HOTEL_SERVICE_UNAVAILABLE", "caido", HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(BookingException.class, () -> service.confirmarPago(12L, 3, pago(10L, "MOCK-3", "300000.00")));

        assertEquals(EstadoGrupo.COMPLETO, grupo.getEstado());
        assertEquals(EstadoParte.PAGADA, grupo.getPartes().get(2).getEstado());
    }

    @Test
    void reintentarConElMismoTokenRepiteElResultadoYConGrupoCompletoReintentaConfirmar() {
        service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000"));
        assertEquals("PARTE_PAGADA", service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000")).estado());

        grupo.setEstado(EstadoGrupo.COMPLETO);
        when(bookingService.confirmarReservaPagada(12L, "GRUPO-30")).thenReturn(ConfirmacionPagoResponse.confirmada());
        assertEquals("CONFIRMADA", service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000")).estado());

        grupo.setEstado(EstadoGrupo.CONFIRMADO);
        assertEquals("CONFIRMADA", service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000")).estado());

        grupo.setEstado(EstadoGrupo.VENCIDO);
        grupo.setMotivoCierre("PAGO_EN_GRUPO_VENCIDO");
        ConfirmacionPagoResponse vencido = service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000"));
        assertEquals("CANCELADA", vencido.estado());
        assertEquals("PAGO_EN_GRUPO_VENCIDO", vencido.motivo());
    }

    @Test
    void otroCobroSobreUnaPartePagadaEsDuplicado() {
        service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000"));

        ConfirmacionPagoResponse r = service.confirmarPago(12L, 2, pago(9L, "MP-999", "300000"));

        assertEquals("RECHAZADA", r.estado());
        assertEquals("PAGO_DUPLICADO", r.motivo());
        assertEquals("MOCK-2", grupo.getPartes().get(1).getTokenPago());
    }

    @Test
    void laParteTieneQueSerDeQuienPagaYPorSuMonto() {
        assertEquals("PARTE_NO_ES_DEL_PAGADOR", service.confirmarPago(12L, 2, pago(10L, "MOCK-2", "300000")).motivo());
        assertEquals("MONTO_NO_COINCIDE", service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "299999.99")).motivo());
        assertEquals("PARTE_NO_ENCONTRADA", service.confirmarPago(12L, 8, pago(9L, "MOCK-2", "300000")).motivo());
        assertEquals(EstadoParte.TOMADA, grupo.getPartes().get(1).getEstado());
    }

    @Test
    void unaParteLiberadaPorElOrganizadorYaNoEsDeQuienTeniaElPagoPendiente() {
        grupo.getPartes().get(1).liberar();

        assertEquals("PARTE_NO_ES_DEL_PAGADOR", service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000")).motivo());
    }

    @Test
    void unPagoDespuesDelPlazoOConElGrupoCerradoSeCancelaParaReembolsar() {
        grupo.setVenceEn(AHORA);
        ConfirmacionPagoResponse tarde = service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000"));
        assertEquals("CANCELADA", tarde.estado());
        assertEquals("PAGO_EN_GRUPO_VENCIDO", tarde.motivo());

        grupo.setVenceEn(AHORA.plusHours(1));
        grupo.setEstado(EstadoGrupo.CANCELADO);
        grupo.setMotivoCierre("GRUPO_CANCELADO");
        assertEquals("GRUPO_CANCELADO", service.confirmarPago(12L, 2, pago(9L, "MOCK-2", "300000")).motivo());
        assertEquals(EstadoParte.TOMADA, grupo.getPartes().get(1).getEstado());
    }

    @Test
    void siLasPartesNoSumanElTotalElGrupoSeCierraYSeReembolsaTodo() {
        grupo.getPartes().get(0).pagar("MOCK-1", AHORA);
        grupo.getPartes().get(1).pagar("MOCK-2", AHORA);
        grupo.getPartes().get(2).setMonto(new BigDecimal("299999.00"));

        ConfirmacionPagoResponse r = service.confirmarPago(12L, 3, pago(10L, "MOCK-3", "299999.00"));

        assertEquals("CANCELADA", r.estado());
        assertEquals("MONTO_NO_COINCIDE", r.motivo());
        verify(cierre).cerrar(30L, EstadoGrupo.CANCELADO, "MONTO_NO_COINCIDE", false);
        verify(bookingService, never()).confirmarReservaPagada(anyLong(), anyString());
    }

    @Test
    void unaReservaSinGrupoSeRechazaParaReembolsar() {
        when(grupoRepository.findByReservaIdForUpdate(99L)).thenReturn(Optional.empty());

        assertEquals("PARTE_NO_ENCONTRADA", service.confirmarPago(99L, 1, pago(9L, "MOCK-2", "300000")).motivo());
        verify(cierre, never()).cerrar(anyLong(), any(), anyString(), anyBoolean());
    }
}
