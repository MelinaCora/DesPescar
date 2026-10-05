package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.PaymentClient;
import com.despescar.reservationservice.dto.pagos.ReembolsoGrupoResponse;
import com.despescar.reservationservice.entity.GrupoPago;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.enums.EstadoGrupo;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.GrupoPagoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class GrupoPagoSchedulerTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 15, 0);

    @Mock
    private GrupoPagoRepository grupoRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private GrupoCierre cierre;
    @Mock
    private PagoParteService pagoParteService;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private InventarioCarrito inventario;
    @Mock
    private PlatformTransactionManager transactionManager;

    private GrupoPagoScheduler scheduler;
    private GrupoPago grupo;

    @BeforeEach
    void setUp() {
        scheduler = new GrupoPagoScheduler(grupoRepository, cierre, pagoParteService, paymentClient,
                new CarritoSoporte(bookingRepository, RELOJ, transactionManager, inventario), new TransactionTemplate(transactionManager));
        grupo = new GrupoPago();
        grupo.setId(30L);
        grupo.setReservation(Reservation.builder().id(12L).creadorId(7L).build());
        grupo.setEstado(EstadoGrupo.VENCIDO);
        grupo.setMotivoCierre("PAGO_EN_GRUPO_VENCIDO");
        grupo.setReembolsosPendientes(true);
        lenient().when(grupoRepository.findById(30L)).thenReturn(Optional.of(grupo));
        lenient().when(grupoRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(grupo));
    }

    @Test
    void cierraLosGruposAbiertosVencidosYUnErrorNoFrenaAlResto() {
        when(grupoRepository.idsVencidos(EstadoGrupo.ABIERTO, AHORA)).thenReturn(List.of(30L, 31L));
        doThrow(new IllegalStateException("lock")).when(cierre)
                .cerrar(30L, EstadoGrupo.VENCIDO, GrupoCierre.MOTIVO_VENCIDO, true);

        scheduler.cerrarVencidos();

        verify(cierre).cerrar(30L, EstadoGrupo.VENCIDO, GrupoCierre.MOTIVO_VENCIDO, true);
        verify(cierre).cerrar(31L, EstadoGrupo.VENCIDO, GrupoCierre.MOTIVO_VENCIDO, true);
    }

    @Test
    void reintentaLaConfirmacionDeLosCompletosQueLlevanDosMinutosSinCambios() {
        when(grupoRepository.idsSinCambiosDesde(EstadoGrupo.COMPLETO, AHORA.minusMinutes(2))).thenReturn(List.of(30L));
        when(pagoParteService.finalizar(30L, 12L))
                .thenThrow(new BookingException("HOTEL_SERVICE_UNAVAILABLE", "caido", HttpStatus.SERVICE_UNAVAILABLE));

        scheduler.reintentarConfirmaciones();

        verify(pagoParteService).finalizar(30L, 12L);
    }

    @Test
    void pideLosReembolsosConElMotivoDelCierreYBorraLaMarca() {
        when(grupoRepository.idsConReembolsosPendientes()).thenReturn(List.of(30L));
        when(paymentClient.reembolsarGrupo(12L, "PAGO_EN_GRUPO_VENCIDO")).thenReturn(new ReembolsoGrupoResponse(2, 1, 0));

        scheduler.pedirReembolsos();

        assertFalse(grupo.isReembolsosPendientes());
        verify(grupoRepository).findByIdForUpdate(30L);
    }

    @Test
    void siPaymentServiceNoRespondeLaMarcaQuedaParaElProximoCiclo() {
        when(grupoRepository.idsConReembolsosPendientes()).thenReturn(List.of(30L));
        when(paymentClient.reembolsarGrupo(12L, "PAGO_EN_GRUPO_VENCIDO"))
                .thenThrow(new BookingException("PAYMENT_SERVICE_UNAVAILABLE", "caido", HttpStatus.SERVICE_UNAVAILABLE));

        scheduler.pedirReembolsos();

        assertTrue(grupo.isReembolsosPendientes());
        verify(grupoRepository, never()).findByIdForUpdate(30L);
    }

    @Test
    void unReembolsoQueElProveedorRechazoNoSeReintentaSolo() {
        when(grupoRepository.idsConReembolsosPendientes()).thenReturn(List.of(30L));
        when(paymentClient.reembolsarGrupo(12L, "PAGO_EN_GRUPO_VENCIDO")).thenReturn(new ReembolsoGrupoResponse(1, 0, 1));

        scheduler.pedirReembolsos();

        // queda "Reembolso manual pendiente" en el historial del pago (CB5): acá ya no hay nada que reintentar
        assertFalse(grupo.isReembolsosPendientes());
    }

    @Test
    void sinNadaPendienteNoLlamaANadie() {
        when(grupoRepository.idsVencidos(EstadoGrupo.ABIERTO, AHORA)).thenReturn(List.of());
        when(grupoRepository.idsSinCambiosDesde(any(), any())).thenReturn(List.of());
        when(grupoRepository.idsConReembolsosPendientes()).thenReturn(List.of());

        scheduler.cerrarVencidos();
        scheduler.reintentarConfirmaciones();
        scheduler.pedirReembolsos();

        verifyNoInteractions(cierre, pagoParteService, paymentClient);
    }
}
