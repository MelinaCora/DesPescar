package com.despescar.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.PaymentResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.OperacionNoDisponibleException;
import com.despescar.payment_service.mapper.PaymentMapper;
import com.despescar.payment_service.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentConciliationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private MercadoPagoWebhookService mercadoPagoWebhookService;
    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentConciliationService service;

    private Payment pago;

    @BeforeEach
    void setUp() {
        pago = Payment.builder()
                .id(UUID.randomUUID())
                .reservationId(12L)
                .userId(7L)
                .amount(new BigDecimal("1060000.00"))
                .status(PaymentStatus.PENDING)
                .currency("ARS")
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private PaymentGatewayResponse mercadoPago(String externalReference) {
        return PaymentGatewayResponse.builder()
                .approved(true).transactionId("123456").externalReference(externalReference).status("approved").build();
    }

    @Test
    void aplicaElEstadoDeMercadoPagoComoElWebhook() {
        PaymentGatewayResponse mp = mercadoPago(pago.getId().toString());
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        when(paymentRepository.findByIdParaActualizar(pago.getId())).thenReturn(Optional.of(pago));
        when(paymentGatewayService.getPaymentStatus("123456")).thenReturn(mp);
        when(mercadoPagoWebhookService.aplicarEstado(pago, mp)).thenReturn(pago);
        when(paymentMapper.toResponse(pago)).thenReturn(PaymentResponse.builder().id(pago.getId()).build());

        PaymentResponse r = service.conciliar(pago.getId(), "123456", 7L);

        assertThat(r.getId()).isEqualTo(pago.getId());
        verify(mercadoPagoWebhookService).aplicarEstado(pago, mp);
    }

    @Test
    void unPagoDeMercadoPagoDeOtraReferenciaResponde409() {
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        when(paymentRepository.findByIdParaActualizar(pago.getId())).thenReturn(Optional.of(pago));
        when(paymentGatewayService.getPaymentStatus("123456")).thenReturn(mercadoPago(UUID.randomUUID().toString()));

        assertThatThrownBy(() -> service.conciliar(pago.getId(), "123456", 7L))
                .isInstanceOf(InvalidPaymentStateException.class);
        verify(mercadoPagoWebhookService, never()).aplicarEstado(any(), any());
    }

    @Test
    void unPagoAjenoResponde403SinConsultarAMercadoPago() {
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        when(paymentRepository.findByIdParaActualizar(pago.getId())).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> service.conciliar(pago.getId(), "123456", 99L))
                .isInstanceOf(AccessDeniedException.class);
        verify(paymentGatewayService, never()).getPaymentStatus(any());
    }

    @Test
    void conElProveedorMockNoExiste() {
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MOCK);

        assertThatThrownBy(() -> service.conciliar(pago.getId(), "123456", 7L))
                .isInstanceOf(OperacionNoDisponibleException.class);
    }

    @Test
    void unPagoQueNoEsDeMercadoPagoNoExisteParaLaConciliacion() {
        pago.setProvider(PaymentProvider.MOCK);
        when(paymentGatewayService.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        when(paymentRepository.findByIdParaActualizar(pago.getId())).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> service.conciliar(pago.getId(), "123456", 7L))
                .isInstanceOf(OperacionNoDisponibleException.class);
        verify(paymentGatewayService, never()).getPaymentStatus(any());
    }
}
