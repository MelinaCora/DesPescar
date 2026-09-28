package com.despescar.payment_service.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    @Autowired
    private PaymentGatewayService paymentGatewayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        Mockito.reset(paymentGatewayService);
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void createPaymentShouldPersistPaymentAndHistory() throws Exception {
        Mockito.when(paymentGatewayService.createCheckout(Mockito.anyString(), Mockito.eq(new BigDecimal("1250.50")), Mockito.eq("ARS"), Mockito.eq(PaymentMethod.CREDIT_CARD)))
                .thenReturn(PaymentCheckoutResponse.builder()
                        .preferenceId("pref-123")
                        .checkoutUrl("https://checkout.test/pref-123")
                        .message("ok")
                        .build());

        String body = """
                {
                  "reservationId": "%s",
                  "userId": "%s",
                  "amount": 1250.50,
                  "paymentMethod": "CREDIT_CARD",
                  "currency": "ARS"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/payments")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.preferenceId").value("pref-123"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.test/pref-123"));

        List<Payment> payments = paymentRepository.findAll();
        org.assertj.core.api.Assertions.assertThat(payments).hasSize(1);
        Payment savedPayment = payments.get(0);
        org.assertj.core.api.Assertions.assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(savedPayment.getPreferenceId()).isEqualTo("pref-123");
        org.assertj.core.api.Assertions.assertThat(savedPayment.getCheckoutUrl()).isEqualTo("https://checkout.test/pref-123");

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
                  "reservationId": "%s",
                  "amount": -1,
                  "currency": "peso"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/payments")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("User ID is required")))
                .andExpect(jsonPath("$.message", containsString("Amount must be greater than zero")));
    }

    @Test
    void cancelPaymentShouldReturnConflictForApprovedPayment() throws Exception {
        Payment payment = payment(UUID.randomUUID(), PaymentStatus.APPROVED);
        paymentRepository.save(payment);

        mockMvc.perform(delete("/api/payments/{paymentId}", payment.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("cannot be cancelled")));
    }

    @Test
    void getHistoryByPaymentShouldReturnOrderedEntries() throws Exception {
        Payment payment = payment(UUID.randomUUID(), PaymentStatus.PENDING);
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
    void webhookShouldUpdatePaymentStatusAndWriteHistory() throws Exception {
        Payment payment = payment(UUID.randomUUID(), PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        Mockito.when(paymentGatewayService.getPaymentStatus("mp-445"))
                .thenReturn(PaymentGatewayResponse.builder()
                        .approved(true)
                        .transactionId("mp-445")
                        .externalReference(payment.getId().toString())
                        .status("approved")
                        .message("approved")
                        .build());

        mockMvc.perform(post("/api/payments/mercadopago/webhook")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "payment",
                                  "data": {
                                    "id": "mp-445"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        org.assertj.core.api.Assertions.assertThat(updatedPayment.getTransactionId()).isEqualTo("mp-445");
        org.assertj.core.api.Assertions.assertThat(paymentHistoryRepository.findByPayment_IdOrderByChangedAtAsc(payment.getId()))
                .extracting(PaymentHistory::getStatus)
                .containsExactly(PaymentStatus.APPROVED);
    }

    private Payment payment(UUID reservationId, PaymentStatus status) {
        return Payment.builder()
                .reservationId(reservationId)
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("1250.50"))
                .status(status)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("ARS")
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
