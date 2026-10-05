package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.ParteGrupo;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class GrupoCierreTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    @Mock
    private GrupoPagoRepository grupoRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private PlatformTransactionManager transactionManager;

    private GrupoCierre cierre;
    private Reservation reserva;
    private GrupoPago grupo;

    @BeforeEach
    void setUp() {
        cierre = new GrupoCierre(grupoRepository, bookingRepository, inventario,
                new CarritoSoporte(bookingRepository, RELOJ, transactionManager, inventario), new TransactionTemplate(transactionManager));
        reserva = Reservation.builder().id(12L).creadorId(7L).cantidadPasajeros(1)
                .tipoPago(PaymentType.SPLIT_PAYMENT).estado(ReservationStatus.ESPERANDO_PAGADORES)
                .limiteTiempo(AHORA.minusMinutes(1)).build();
        reserva.getDetalles().add(ReservationDetail.builder().reservation(reserva).outboundSeatNumber("1A")
                .priceCharged(new BigDecimal("240000.00")).paymentStatus(PaymentStatus.PENDIENTE).build());
        EstadiaHotel e = new EstadiaHotel();
        e.setReservation(reserva);
        e.setCheckIn(LocalDate.of(2026, 11, 10));
        e.setCheckOut(LocalDate.of(2026, 11, 12));
        e.setRetencionId(UUID.randomUUID());
        e.setPrecioTotal(new BigDecimal("60000.00"));
        reserva.getEstadias().add(e);
        grupo = new GrupoPago();
        grupo.setId(30L);
        grupo.setReservation(reserva);
        grupo.setOrganizadorId(7L);
        grupo.setEstado(EstadoGrupo.ABIERTO);
        grupo.setVenceEn(AHORA.minusMinutes(1));
        ParteGrupo uno = ParteGrupo.libre(1, new BigDecimal("150000.00"));
        uno.tomar(7L, null);
        grupo.agregarParte(uno);
        grupo.agregarParte(ParteGrupo.libre(2, new BigDecimal("150000.00")));
        lenient().when(grupoRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(grupo));
        lenient().when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(reserva));
    }

    @Test
    void cierraElGrupoVencidoLiberaTodoYMarcaReembolsosSiHuboPagos() {
        grupo.getPartes().get(0).pagar("MOCK-1", AHORA.minusHours(3));

        assertTrue(cierre.cerrar(30L, EstadoGrupo.VENCIDO, GrupoCierre.MOTIVO_VENCIDO, true));

        assertEquals(EstadoGrupo.VENCIDO, grupo.getEstado());
        assertEquals("PAGO_EN_GRUPO_VENCIDO", grupo.getMotivoCierre());
        assertTrue(grupo.isReembolsosPendientes());
        assertEquals(AHORA, grupo.getActualizadoEn());
        assertEquals(ReservationStatus.CANCELADA, reserva.getEstado());
        assertEquals("PAGO_EN_GRUPO_VENCIDO", reserva.getMotivoCancelacion());
        assertEquals(PaymentStatus.CANCELADO, reserva.getDetalles().get(0).getPaymentStatus());
        // grupo y reserva bloqueados → asientos en la transacción → commit → retenciones por HTTP
        InOrder orden = inOrder(grupoRepository, bookingRepository, inventario, transactionManager);
        orden.verify(grupoRepository).findByIdForUpdate(30L);
        orden.verify(bookingRepository).findByIdForUpdate(12L);
        orden.verify(inventario).liberarAsientos(reserva);
        orden.verify(transactionManager).commit(any());
        orden.verify(inventario).liberarRetenciones(reserva);
    }

    @Test
    void sinPartesPagadasIgualSePideCancelarLosPagosPendientes() {
        assertTrue(cierre.cerrar(30L, EstadoGrupo.CANCELADO, GrupoCierre.MOTIVO_CANCELADO, false));

        assertEquals(EstadoGrupo.CANCELADO, grupo.getEstado());
        // payment-service cancela los pagos PENDING de las partes aunque no haya nada que reembolsar
        assertTrue(grupo.isReembolsosPendientes());
    }

    @Test
    void unGrupoQueYaNoEstaAbiertoNoSeToca() {
        grupo.setEstado(EstadoGrupo.COMPLETO);

        assertFalse(cierre.cerrar(30L, EstadoGrupo.VENCIDO, GrupoCierre.MOTIVO_VENCIDO, true));

        assertEquals(EstadoGrupo.COMPLETO, grupo.getEstado());
        verifyNoInteractions(inventario);
    }

    @Test
    void elVencimientoReleeElPlazoConElLock() {
        grupo.setVenceEn(AHORA.plusMinutes(5));

        assertFalse(cierre.cerrar(30L, EstadoGrupo.VENCIDO, GrupoCierre.MOTIVO_VENCIDO, true));

        assertEquals(EstadoGrupo.ABIERTO, grupo.getEstado());
        verifyNoInteractions(inventario);
    }

    @Test
    void sinLugarAlConfirmarSoloSeMarcaElGrupoCompletoYSeReembolsa() {
        grupo.setEstado(EstadoGrupo.COMPLETO);

        cierre.marcarCanceladoTrasConfirmar(30L, "SIN_DISPONIBILIDAD");

        assertEquals(EstadoGrupo.CANCELADO, grupo.getEstado());
        assertEquals("SIN_DISPONIBILIDAD", grupo.getMotivoCierre());
        assertTrue(grupo.isReembolsosPendientes());
        verifyNoInteractions(inventario);
    }

    @Test
    void marcarCanceladoNoTocaUnGrupoQueNoEstabaCompleto() {
        cierre.marcarCanceladoTrasConfirmar(30L, "SIN_DISPONIBILIDAD");

        assertEquals(EstadoGrupo.ABIERTO, grupo.getEstado());
    }

    @Test
    void unaReservaQueNoEsperaPagadoresNoSeCancelaAlCerrarElGrupo() {
        reserva.setEstado(ReservationStatus.CONFIRMADA);

        assertFalse(cierre.cerrar(30L, EstadoGrupo.VENCIDO, GrupoCierre.MOTIVO_VENCIDO, true));

        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        assertEquals(PaymentStatus.PENDIENTE, reserva.getDetalles().get(0).getPaymentStatus());
        assertEquals(EstadoGrupo.ABIERTO, grupo.getEstado());
        assertFalse(grupo.isReembolsosPendientes());
        verifyNoInteractions(inventario);
    }

    @Test
    void elGrupoCompletoQueNoSePudoConfirmarCancelaLaReservaLiberaTodoYReembolsa() {
        grupo.setEstado(EstadoGrupo.COMPLETO);
        grupo.setCompletoDesde(AHORA.minusMinutes(31));

        assertTrue(cierre.cancelarSinConfirmar(30L, GrupoCierre.MOTIVO_SIN_CONFIRMAR));

        assertEquals(EstadoGrupo.CANCELADO, grupo.getEstado());
        assertEquals("CONFIRMACION_FALLIDA", grupo.getMotivoCierre());
        assertTrue(grupo.isReembolsosPendientes());
        assertEquals(AHORA, grupo.getActualizadoEn());
        assertEquals(ReservationStatus.CANCELADA, reserva.getEstado());
        assertEquals("CONFIRMACION_FALLIDA", reserva.getMotivoCancelacion());
        assertEquals(PaymentStatus.CANCELADO, reserva.getDetalles().get(0).getPaymentStatus());
        // grupo y reserva bloqueados → asientos en la transacción → commit → retenciones por HTTP
        InOrder orden = inOrder(grupoRepository, bookingRepository, inventario, transactionManager);
        orden.verify(grupoRepository).findByIdForUpdate(30L);
        orden.verify(bookingRepository).findByIdForUpdate(12L);
        orden.verify(inventario).liberarAsientos(reserva);
        orden.verify(transactionManager).commit(any());
        orden.verify(inventario).liberarRetenciones(reserva);
    }

    @Test
    void siUnaConfirmacionEnCursoGanoNoSeCancelaNiSeReembolsa() {
        grupo.setEstado(EstadoGrupo.COMPLETO);
        reserva.setEstado(ReservationStatus.CONFIRMADA);

        assertFalse(cierre.cancelarSinConfirmar(30L, GrupoCierre.MOTIVO_SIN_CONFIRMAR));

        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        assertEquals(EstadoGrupo.COMPLETO, grupo.getEstado());
        assertFalse(grupo.isReembolsosPendientes());
        verifyNoInteractions(inventario);
    }

    @Test
    void conLaReservaYaCanceladaSoloSeCierraElGrupoYSeReembolsa() {
        grupo.setEstado(EstadoGrupo.COMPLETO);
        reserva.setEstado(ReservationStatus.CANCELADA);
        reserva.setMotivoCancelacion("SIN_DISPONIBILIDAD");

        assertTrue(cierre.cancelarSinConfirmar(30L, GrupoCierre.MOTIVO_SIN_CONFIRMAR));

        assertEquals(EstadoGrupo.CANCELADO, grupo.getEstado());
        assertTrue(grupo.isReembolsosPendientes());
        assertEquals("SIN_DISPONIBILIDAD", reserva.getMotivoCancelacion());
        verifyNoInteractions(inventario);
    }

    @Test
    void cancelarSinConfirmarNoTocaUnGrupoQueNoEstaCompleto() {
        assertFalse(cierre.cancelarSinConfirmar(30L, GrupoCierre.MOTIVO_SIN_CONFIRMAR));

        assertEquals(EstadoGrupo.ABIERTO, grupo.getEstado());
        assertEquals(ReservationStatus.ESPERANDO_PAGADORES, reserva.getEstado());
        verify(bookingRepository, never()).findByIdForUpdate(12L);
        verifyNoInteractions(inventario);
    }
}
