package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ConfirmacionReservaResponse;
import com.despescar.payment_service.dto.request.OrdenPagoRequest;
import com.despescar.payment_service.dto.response.OrdenPagoResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.OperacionNoDisponibleException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.service.MercadoPagoOrdenGateway.CrearOrden;

/**
 * Flujo completo de POST /{id}/orden con el gateway de ordenes simulado y los servicios reales de
 * aplicacion de estado y aprobacion (reservation-service y el repositorio son dobles).
 */
class MercadoPagoOrdenServiceTest {

    private static final BigDecimal TOTAL = new BigDecimal("1250.00");

    /** Un doble que es a la vez PaymentGatewayService y MercadoPagoOrdenGateway, como el real. */
    interface GatewayDeOrdenes extends PaymentGatewayService, MercadoPagoOrdenGateway {
    }

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentHistoryService paymentHistoryService = mock(PaymentHistoryService.class);
    private final ReservationClient reservationClient = mock(ReservationClient.class);
    private final GatewayDeOrdenes gateway = mock(GatewayDeOrdenes.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MercadoPagoOrdenGateway> proveedor = mock(ObjectProvider.class);

    private MercadoPagoOrdenService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        when(gateway.provider()).thenReturn(PaymentProvider.MERCADO_PAGO_ORDERS);
        when(proveedor.getIfAvailable()).thenReturn(gateway);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        AprobacionPagoService aprobacion = new AprobacionPagoService(
                paymentRepository, paymentHistoryService, gateway, reservationClient);
        MercadoPagoWebhookService webhook = new MercadoPagoWebhookService(
                paymentRepository, gateway, paymentHistoryService, aprobacion);
        service = new MercadoPagoOrdenService(
                paymentRepository, gateway, proveedor, paymentHistoryService, webhook, new PaymentMapper());

        payment = Payment.builder()
                .id(UUID.randomUUID())
                .reservationId(12L)
                .userId(7L)
                .amount(TOTAL)
                .status(PaymentStatus.PENDING)
                .currency("ARS")
                .provider(PaymentProvider.MERCADO_PAGO_ORDERS)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByIdParaActualizar(payment.getId())).thenReturn(Optional.of(payment));
    }

    private static OrdenMercadoPago orden(String status, String detail) {
        return new OrdenMercadoPago("ORD01", status, status, "ref", TOTAL, TOTAL,
                "PAY01", status, detail, "credit_card", "master");
    }

    private static OrdenPagoRequest pedido() {
        return new OrdenPagoRequest("tok123", "master", "credit_card", 1, "comprador@testuser.com");
    }

    @Test
    void aprobadaConfirmaLaReservaYElPagoQuedaAprobadoConLaOrdenComoCobro() {
        when(gateway.crearOrden(any())).thenReturn(orden("processed", "accredited"));
        when(reservationClient.confirmarPago(12L, 7L, "ORD01", TOTAL))
                .thenReturn(new ConfirmacionReservaResponse("CONFIRMADA", null, "ok"));

        OrdenPagoResponse r = service.cobrar(payment.getId(), pedido(), 7L);

        assertThat(r.pago().getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(r.pago().getTransactionId()).isEqualTo("ORD01");
        assertThat(r.pago().getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(r.estadoOrden()).isEqualTo("processed");
        assertThat(r.detalle()).isEqualTo("accredited");

        ArgumentCaptor<CrearOrden> captor = ArgumentCaptor.forClass(CrearOrden.class);
        verify(gateway).crearOrden(captor.capture());
        assertThat(captor.getValue().paymentId()).isEqualTo(payment.getId().toString());
        assertThat(captor.getValue().amount()).isEqualByComparingTo(TOTAL);
        assertThat(captor.getValue().token()).isEqualTo("tok123");
        assertThat(captor.getValue().installments()).isEqualTo(1);
        verify(gateway, never()).refund(any(), any());
    }

    @Test
    void siReservationServiceRechazaSeReembolsaLaOrden() {
        when(gateway.crearOrden(any())).thenReturn(orden("processed", "accredited"));
        when(reservationClient.confirmarPago(12L, 7L, "ORD01", TOTAL))
                .thenReturn(ConfirmacionReservaResponse.rechazada("SIN_DISPONIBILIDAD", "sin lugar"));
        when(gateway.refund("ORD01", TOTAL)).thenReturn(
                RefundGatewayResponse.builder().approved(true).refundTransactionId("REF01").build());

        OrdenPagoResponse r = service.cobrar(payment.getId(), pedido(), 7L);

        assertThat(r.pago().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(gateway).refund("ORD01", TOTAL);
    }

    @Test
    void rechazadaDejaElPagoRejectedConElDetalleSinTocarLaReserva() {
        when(gateway.crearOrden(any())).thenReturn(orden("failed", "insufficient_amount"));

        OrdenPagoResponse r = service.cobrar(payment.getId(), pedido(), 7L);

        assertThat(r.pago().getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(r.pago().getTransactionId()).isEqualTo("ORD01");
        assertThat(r.detalle()).isEqualTo("insufficient_amount");
        verify(reservationClient, never()).confirmarPago(any(), any(), any(), any());
        verify(gateway, never()).refund(any(), any());
    }

    @Test
    void enProcesoSigueApendienteGuardandoLaOrdenYUnSegundoIntentoLaRelee() {
        when(gateway.crearOrden(any())).thenReturn(orden("processing", "in_process"));

        OrdenPagoResponse primero = service.cobrar(payment.getId(), pedido(), 7L);

        assertThat(primero.pago().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(primero.pago().getTransactionId()).isEqualTo("ORD01");
        verify(paymentHistoryService).saveHistory(eq(payment), eq(PaymentStatus.PENDING), any());

        // Mientras tanto Mercado Pago la aprobo: el segundo intento no crea otra orden.
        when(gateway.consultarOrden("ORD01")).thenReturn(orden("processed", "accredited"));
        when(reservationClient.confirmarPago(12L, 7L, "ORD01", TOTAL))
                .thenReturn(new ConfirmacionReservaResponse("CONFIRMADA", null, "ok"));

        OrdenPagoResponse segundo = service.cobrar(payment.getId(), pedido(), 7L);

        assertThat(segundo.pago().getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(gateway).crearOrden(any());
    }

    @Test
    void soloElDuenoYSoloUnPagoPendiente() {
        assertThatThrownBy(() -> service.cobrar(payment.getId(), pedido(), 99L))
                .isInstanceOf(AccessDeniedException.class);

        payment.setStatus(PaymentStatus.APPROVED);
        assertThatThrownBy(() -> service.cobrar(payment.getId(), pedido(), 7L))
                .isInstanceOf(InvalidPaymentStateException.class);
        verify(gateway, never()).crearOrden(any());
    }

    @Test
    void conOtroProveedorActivoRespondeNoDisponible() {
        when(proveedor.getIfAvailable()).thenReturn(null);
        when(gateway.provider()).thenReturn(PaymentProvider.MOCK);

        assertThatThrownBy(() -> service.cobrar(payment.getId(), pedido(), 7L))
                .isInstanceOf(OperacionNoDisponibleException.class);
        assertThat(service.configuracion().provider()).isEqualTo(PaymentProvider.MOCK);
        assertThat(service.configuracion().publicKey()).isNull();
    }

    @Test
    void unPagoDeOtroProveedorNoSeCobraPorOrden() {
        payment.setProvider(PaymentProvider.MOCK);

        assertThatThrownBy(() -> service.cobrar(payment.getId(), pedido(), 7L))
                .isInstanceOf(OperacionNoDisponibleException.class);
    }

    @Test
    void laConfiguracionEntregaLaPublicKey() {
        when(gateway.publicKey()).thenReturn("APP_USR-publica");

        assertThat(service.configuracion().provider()).isEqualTo(PaymentProvider.MERCADO_PAGO_ORDERS);
        assertThat(service.configuracion().publicKey()).isEqualTo("APP_USR-publica");
    }
}
