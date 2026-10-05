package com.despescar.payment_service.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ConfirmacionReservaResponse;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.exception.ReservationClientException;
import com.despescar.payment_service.repository.PaymentHistoryRepository;
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.service.PaymentGatewayService;

@SpringBootTest
class PaymentEndpointsTest {

    private static final BigDecimal TOTAL = new BigDecimal("1060000.00");

    @TestConfiguration
    static class Dobles {
        @Bean
        @Primary
        PaymentGatewayService paymentGatewayService() {
            return Mockito.mock(PaymentGatewayService.class);
        }

        @Bean
        @Primary
        ReservationClient reservationClient() {
            return Mockito.mock(ReservationClient.class);
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;
    @Autowired
    private PaymentGatewayService gateway;
    @Autowired
    private ReservationClient reservationClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user("7").roles("CLIENTE")))
                .build();
        Mockito.reset(gateway, reservationClient);
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private Payment pago(Long userId, PaymentProvider provider, PaymentStatus status) {
        return paymentRepository.save(Payment.builder()
                .reservationId(12L)
                .userId(userId)
                .amount(TOTAL)
                .status(status)
                .currency("ARS")
                .provider(provider)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void reservaResponde(Payment pago, String estado, String motivo) {
        Mockito.when(reservationClient.confirmarPago(12L, 7L, "MOCK-" + pago.getId(), TOTAL))
                .thenReturn(new ConfirmacionReservaResponse(estado, motivo, "mensaje"));
    }

    private String simulacion(boolean aprobado) {
        return "{\"aprobado\":" + aprobado + "}";
    }

    @Test
    void simulacionAprobadaConfirmaLaReserva() throws Exception {
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MOCK);
        Payment pago = pago(7L, PaymentProvider.MOCK, PaymentStatus.PENDING);
        reservaResponde(pago, "CONFIRMADA", null);

        mockMvc.perform(post("/api/payments/{id}/simulacion", pago.getId())
                        .contentType(APPLICATION_JSON).content(simulacion(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.transactionId").value("MOCK-" + pago.getId()));
    }

    @Test
    void simulacionRechazadaNoTocaLaReserva() throws Exception {
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MOCK);
        Payment pago = pago(7L, PaymentProvider.MOCK, PaymentStatus.PENDING);

        mockMvc.perform(post("/api/payments/{id}/simulacion", pago.getId())
                        .contentType(APPLICATION_JSON).content(simulacion(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        Mockito.verifyNoInteractions(reservationClient);
    }

    @Test
    void siLaReservaNoSeConfirmaElPagoQuedaReembolsado() throws Exception {
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MOCK);
        Payment pago = pago(7L, PaymentProvider.MOCK, PaymentStatus.PENDING);
        reservaResponde(pago, "CANCELADA", "PAGO_TARDIO_SIN_DISPONIBILIDAD");
        Mockito.when(gateway.refund("MOCK-" + pago.getId(), TOTAL))
                .thenReturn(RefundGatewayResponse.builder().approved(true).refundTransactionId("MOCK-REFUND-1").build());

        mockMvc.perform(post("/api/payments/{id}/simulacion", pago.getId())
                        .contentType(APPLICATION_JSON).content(simulacion(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void siReservasNoRespondeDa502YElPagoSiguePendiente() throws Exception {
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MOCK);
        Payment pago = pago(7L, PaymentProvider.MOCK, PaymentStatus.PENDING);
        Mockito.when(reservationClient.confirmarPago(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new ReservationClientException("caido"));

        mockMvc.perform(post("/api/payments/{id}/simulacion", pago.getId())
                        .contentType(APPLICATION_JSON).content(simulacion(true)))
                .andExpect(status().isBadGateway());

        Assertions.assertThat(paymentRepository.findById(pago.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void simulacionAjenaYaProcesadaOSinCuerpo() throws Exception {
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MOCK);
        Payment ajeno = pago(99L, PaymentProvider.MOCK, PaymentStatus.PENDING);
        Payment aprobado = pago(7L, PaymentProvider.MOCK, PaymentStatus.APPROVED);

        mockMvc.perform(post("/api/payments/{id}/simulacion", ajeno.getId())
                        .contentType(APPLICATION_JSON).content(simulacion(true)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payments/{id}/simulacion", aprobado.getId())
                        .contentType(APPLICATION_JSON).content(simulacion(true)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/payments/{id}/simulacion", aprobado.getId())
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("aprobado es obligatorio"));
        mockMvc.perform(post("/api/payments/{id}/simulacion", "no-es-uuid")
                        .contentType(APPLICATION_JSON).content(simulacion(true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simulacionConMercadoPagoYConciliacionConMockNoExisten() throws Exception {
        Payment pago = pago(7L, PaymentProvider.MOCK, PaymentStatus.PENDING);
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        mockMvc.perform(post("/api/payments/{id}/simulacion", pago.getId())
                        .contentType(APPLICATION_JSON).content(simulacion(true)))
                .andExpect(status().isNotFound());

        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MOCK);
        mockMvc.perform(post("/api/payments/{id}/conciliacion", pago.getId())
                        .contentType(APPLICATION_JSON).content("{\"mpPaymentId\":\"123456\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void conciliacionApruebaConElPagoDeMercadoPago() throws Exception {
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        Payment pago = pago(7L, PaymentProvider.MERCADO_PAGO, PaymentStatus.PENDING);
        Mockito.when(gateway.getPaymentStatus("123456")).thenReturn(PaymentGatewayResponse.builder()
                .approved(true).transactionId("123456").externalReference(pago.getId().toString())
                .status("approved").amount(TOTAL).paymentTypeId("credit_card").build());
        Mockito.when(reservationClient.confirmarPago(12L, 7L, "123456", TOTAL))
                .thenReturn(new ConfirmacionReservaResponse("CONFIRMADA", null, "Reserva confirmada."));

        mockMvc.perform(post("/api/payments/{id}/conciliacion", pago.getId())
                        .contentType(APPLICATION_JSON).content("{\"mpPaymentId\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"));
    }

    @Test
    void conciliacionCorrigeUnPagoReembolsadoSiLaReservaSeConfirmoConOtroCobro() throws Exception {
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        Payment pago = pago(7L, PaymentProvider.MERCADO_PAGO, PaymentStatus.REFUNDED);
        pago.setTransactionId("OLD");
        paymentRepository.save(pago);
        Mockito.when(gateway.getPaymentStatus("123456")).thenReturn(PaymentGatewayResponse.builder()
                .approved(true).transactionId("123456").externalReference(pago.getId().toString())
                .status("approved").amount(TOTAL).paymentTypeId("credit_card").build());
        Mockito.when(reservationClient.confirmarPago(12L, 7L, "123456", TOTAL))
                .thenReturn(new ConfirmacionReservaResponse("CONFIRMADA", null, "Reserva confirmada."));

        mockMvc.perform(post("/api/payments/{id}/conciliacion", pago.getId())
                        .contentType(APPLICATION_JSON).content("{\"mpPaymentId\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.transactionId").value("123456"));
        Mockito.verify(gateway, Mockito.never()).refund(Mockito.any(), Mockito.any());
    }

    @Test
    void conciliacionConUnPagoDeOtraReferenciaResponde409() throws Exception {
        Mockito.when(gateway.provider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        Payment pago = pago(7L, PaymentProvider.MERCADO_PAGO, PaymentStatus.PENDING);
        Mockito.when(gateway.getPaymentStatus("123456")).thenReturn(PaymentGatewayResponse.builder()
                .approved(true).transactionId("123456").externalReference("otra").status("approved").build());

        mockMvc.perform(post("/api/payments/{id}/conciliacion", pago.getId())
                        .contentType(APPLICATION_JSON).content("{\"mpPaymentId\":\"123456\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/payments/{id}/conciliacion", pago.getId())
                        .contentType(APPLICATION_JSON).content("{\"mpPaymentId\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinSesionResponde401ConElFormatoDeErroresDelServicio() throws Exception {
        MockMvc sinSesion = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        sinSesion.perform(post("/api/payments").contentType(APPLICATION_JSON).content("{\"reservationId\":12}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/payments"));
    }

    @Test
    void unClienteNoPuedeReembolsarseUnPagoDesdeLaApi() throws Exception {
        Payment pago = pago(7L, PaymentProvider.MOCK, PaymentStatus.APPROVED);

        mockMvc.perform(post("/api/refunds").contentType(APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + pago.getId() + "\",\"amount\":10.00,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        Mockito.verify(gateway, Mockito.never()).refund(Mockito.any(), Mockito.any());
    }
}
