package com.despescar.payment_service.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.despescar.payment_service.client.ReservationClient;
import com.despescar.payment_service.client.dto.ReservationResponse;
import com.despescar.payment_service.dto.response.PaymentCheckoutResponse;
import com.despescar.payment_service.dto.response.PaymentGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.entity.PaymentHistory;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.repository.PaymentHistoryRepository;
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.service.PaymentGatewayService;

@SpringBootTest
class PaymentControllerIntegrationTest {

    @TestConfiguration
    static class PaymentControllerIntegrationTestConfig {
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
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    @Autowired
    private PaymentGatewayService paymentGatewayService;

    @Autowired
    private ReservationClient reservationClient;

    @Value("${mercadopago.webhook.secret}")
    private String webhookSecret;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .defaultRequest(get("/").with(user("88").roles("CLIENTE")))
            .build();
        Mockito.reset(paymentGatewayService, reservationClient);
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void createPaymentShouldPersistPaymentAndHistory() throws Exception {
        ReservationResponse reservation = reservationResponse(88L, "ARS");
        Mockito.when(reservationClient.getReservation(91L)).thenReturn(reservation);
        Mockito.when(paymentGatewayService.createCheckout(Mockito.anyString(), Mockito.eq(new BigDecimal("1250.50")), Mockito.eq("ARS")))
                .thenReturn(PaymentCheckoutResponse.builder()
                        .preferenceId("pref-123")
                        .checkoutUrl("https://checkout.test/pref-123")
                        .message("ok")
                        .build());

        String body = """
                {
                                    "reservationId": 91
                }
                """;

        mockMvc.perform(post("/api/payments")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(1250.50))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.preferenceId").value("pref-123"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.test/pref-123"));

        List<Payment> payments = paymentRepository.findAll();
        org.assertj.core.api.Assertions.assertThat(payments).hasSize(1);
        Payment savedPayment = payments.get(0);
        org.assertj.core.api.Assertions.assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(savedPayment.getAmount()).isEqualByComparingTo("1250.50");
        org.assertj.core.api.Assertions.assertThat(savedPayment.getPreferenceId()).isEqualTo("pref-123");
        org.assertj.core.api.Assertions.assertThat(savedPayment.getCheckoutUrl()).isEqualTo("https://checkout.test/pref-123");
        org.assertj.core.api.Assertions.assertThat(savedPayment.getPaymentMethod()).isNull();

        List<PaymentHistory> history = paymentHistoryRepository.findByPayment_IdOrderByChangedAtAsc(savedPayment.getId());
        org.assertj.core.api.Assertions.assertThat(history).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(history.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);

        mockMvc.perform(get("/api/payments/{paymentId}", savedPayment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenceId").value("pref-123"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.test/pref-123"));
    }

    @Test
    void createPaymentShouldRejectInvalidPayload() throws Exception {
        String body = """
                {
                  "reservationId": null
                }
                """;

        mockMvc.perform(post("/api/payments")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Reservation ID is required")));
    }

    @Test
    void cancelPaymentShouldReturnConflictForApprovedPayment() throws Exception {
        Payment payment = payment(77L, 88L, PaymentStatus.APPROVED);
        paymentRepository.save(payment);

        mockMvc.perform(delete("/api/payments/{paymentId}", payment.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("cannot be cancelled")));
    }

    @Test
    void getPaymentByIdShouldRejectAnotherUsersPayment() throws Exception {
        Payment payment = payment(77L, 99L, PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        mockMvc.perform(get("/api/payments/{paymentId}", payment.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getHistoryByPaymentShouldReturnOrderedEntries() throws Exception {
        Payment payment = payment(77L, 88L, PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        paymentHistoryRepository.save(PaymentHistory.builder()
                .payment(payment)
                .status(PaymentStatus.PENDING)
                .changedAt(LocalDateTime.now().minusMinutes(2))
                .description("Created")
                .build());
        paymentHistoryRepository.save(PaymentHistory.builder()
                .payment(payment)
                .status(PaymentStatus.CANCELLED)
                .changedAt(LocalDateTime.now().minusMinutes(1))
                .description("Cancelled")
                .build());

        mockMvc.perform(get("/api/payment-history/payment/{paymentId}", payment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));
    }

    @Test
    void webhookShouldRejectInvalidSignatureWithoutChangingPayment() throws Exception {
        Payment payment = payment(77L, 88L, PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        mockMvc.perform(post("/api/payments/mercadopago/webhook?data.id=445&type=payment")
                        .contentType(APPLICATION_JSON)
                        .header("x-signature", "ts=123,v1=invalid")
                        .header("x-request-id", "req-1")
                        .content("""
                                {
                                  "type": "payment",
                                  "data": {
                                    "id": "445"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        Payment unchangedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        Mockito.verifyNoInteractions(paymentGatewayService);
        Mockito.verifyNoInteractions(reservationClient);
    }

    @Test
    void webhookShouldUpdatePaymentStatusAndWriteHistory() throws Exception {
        Payment payment = payment(77L, 88L, PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        Mockito.when(paymentGatewayService.getPaymentStatus("445"))
                .thenReturn(PaymentGatewayResponse.builder()
                        .approved(true)
                        .transactionId("445")
                        .externalReference(payment.getId().toString())
                        .status("approved")
                        .paymentTypeId("credit_card")
                        .message("approved")
                        .build());

        String requestId = "req-1";
        String timestamp = "1727401800";
        String signature = signWebhook(timestamp, requestId, "445");

        mockMvc.perform(post("/api/payments/mercadopago/webhook?data.id=445&type=payment")
                        .contentType(APPLICATION_JSON)
                        .header("x-signature", "ts=" + timestamp + ",v1=" + signature)
                        .header("x-request-id", requestId)
                        .content("""
                                {
                                  "type": "payment",
                                  "data": {
                                    "id": "445"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        org.assertj.core.api.Assertions.assertThat(updatedPayment.getTransactionId()).isEqualTo("445");
        org.assertj.core.api.Assertions.assertThat(updatedPayment.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        org.assertj.core.api.Assertions.assertThat(paymentHistoryRepository.findByPayment_IdOrderByChangedAtAsc(payment.getId()))
                .extracting(PaymentHistory::getStatus)
                .containsExactly(PaymentStatus.APPROVED);
        Mockito.verify(reservationClient).markReservationPaymentPaid(77L, 88L, "445");
    }

    private Payment payment(Long reservationId, Long userId, PaymentStatus status) {
        return Payment.builder()
                .reservationId(reservationId)
                .userId(userId)
                .amount(new BigDecimal("1250.50"))
                .status(status)
                .currency("ARS")
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ReservationResponse reservationResponse(Long userId, String currency) {
        ReservationResponse response = new ReservationResponse();
        response.setIdCarrito(91L);
        response.setMoneda(currency);
        response.setMontoTotal(new BigDecimal("1250.50"));

        ReservationResponse.SeatDetail seat = new ReservationResponse.SeatDetail();
        seat.setPagadorId(userId);
        seat.setPrecioCobrado(new BigDecimal("1250.50"));
        seat.setEstadoPago("PENDIENTE");

        response.setAsientos(List.of(seat));
        return response;
    }

    private String signWebhook(String timestamp, String requestId, String dataId) throws Exception {
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }
}
