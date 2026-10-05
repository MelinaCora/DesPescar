package com.despescar.payment_service.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

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
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user("55").roles("CLIENTE")))
                .build();
        Mockito.reset(paymentGatewayService);
        refundHistoryRepository.deleteAll();
        refundRepository.deleteAll();
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void crearReembolsosDesdeLaApiQuedaCerradoHastaE4() throws Exception {
        Payment payment = paymentRepository.save(approvedPayment());

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
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(refundRepository.findAll()).isEmpty();
        Mockito.verifyNoInteractions(paymentGatewayService);
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
                .reservationId(77L)
                .userId(55L)
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
