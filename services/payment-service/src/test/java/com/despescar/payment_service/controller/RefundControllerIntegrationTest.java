package com.despescar.payment_service.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

import com.despescar.payment_service.dto.response.RefundGatewayResponse;
import com.despescar.payment_service.entity.Payment;
import com.despescar.payment_service.entity.Refund;
import com.despescar.payment_service.enums.PaymentMethod;
import com.despescar.payment_service.enums.PaymentProvider;
import com.despescar.payment_service.enums.PaymentStatus;
import com.despescar.payment_service.enums.RefundStatus;
import com.despescar.payment_service.repository.PaymentHistoryRepository;
import com.despescar.payment_service.repository.PaymentRepository;
import com.despescar.payment_service.repository.RefundHistoryRepository;
import com.despescar.payment_service.repository.RefundRepository;
import com.despescar.payment_service.service.PaymentGatewayService;

@SpringBootTest
class RefundControllerIntegrationTest {

    @TestConfiguration
    static class RefundControllerIntegrationTestConfig {
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
    private RefundRepository refundRepository;

    @Autowired
    private RefundHistoryRepository refundHistoryRepository;

    @Autowired
    private PaymentGatewayService paymentGatewayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        Mockito.reset(paymentGatewayService);
        refundHistoryRepository.deleteAll();
        refundRepository.deleteAll();
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void createRefundShouldPersistApprovedRefundAndHistory() throws Exception {
        Payment payment = approvedPayment();
        payment = paymentRepository.save(payment);

        Mockito.when(paymentGatewayService.refund("mp-123", new BigDecimal("35.00")))
                .thenReturn(RefundGatewayResponse.builder()
                        .approved(true)
                        .refundTransactionId("refund-123")
                        .message("ok")
                        .build());

        String body = """
                {
                  "paymentId": "%s",
                  "amount": 35.00,
                  "reason": "Customer requested cancellation"
                }
                """.formatted(payment.getId());

        mockMvc.perform(post("/api/refunds")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.refundTransactionId").value("refund-123"));

        Refund refund = refundRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(refundHistoryRepository.findByRefund_IdOrderByChangedAtAsc(refund.getId()))
                .extracting(com.despescar.payment_service.entity.RefundHistory::getStatus)
                .containsExactly(RefundStatus.PENDING, RefundStatus.APPROVED);
    }

    @Test
    void createRefundShouldRejectAmountThatExceedsAvailableBalance() throws Exception {
        Payment payment = approvedPayment();
        payment = paymentRepository.save(payment);

        refundRepository.save(Refund.builder()
                .payment(payment)
                .amount(new BigDecimal("90.00"))
                .reason("Previous refund")
                .status(RefundStatus.APPROVED)
                .refundTransactionId("refund-prev")
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build());

        String body = """
                {
                  "paymentId": "%s",
                  "amount": 20.00,
                  "reason": "Customer requested cancellation"
                }
                """.formatted(payment.getId());

        mockMvc.perform(post("/api/refunds")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Available amount: 10.00")));
    }

    @Test
    void getRefundsByUserShouldReturnPersistedRefunds() throws Exception {
        Payment payment = approvedPayment();
        payment = paymentRepository.save(payment);

        Refund refund = refundRepository.save(Refund.builder()
                .payment(payment)
                .amount(new BigDecimal("15.00"))
                .reason("Customer requested cancellation")
                .status(RefundStatus.APPROVED)
                .refundTransactionId("refund-xyz")
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/refunds/user/{userId}", payment.getUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(refund.getId().toString()))
                .andExpect(jsonPath("$[0].paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    private Payment approvedPayment() {
        return Payment.builder()
                .reservationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("ARS")
                .transactionId("mp-123")
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
