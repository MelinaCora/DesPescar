package com.despescar.payment_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.despescar.payment_service.exception.GlobalExceptionHandler;
import com.despescar.payment_service.exception.InvalidPaymentStateException;
import com.despescar.payment_service.exception.ReservationAmountResolutionException;
import com.despescar.payment_service.service.PaymentService;

/** El mapeo de excepciones del servicio a respuestas HTTP, sin levantar el contexto. */
class GlobalExceptionHandlerTest {

    private PaymentService paymentService;
    private MockMvc mockMvc;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockHttpServletRequestBuilder consulta() {
        return get("/api/payments/{id}", id).principal(new UsernamePasswordAuthenticationToken("55", null));
    }

    @Test
    void accesoDenegadoResponde403() throws Exception {
        when(paymentService.getPaymentById(any(), any())).thenThrow(new AccessDeniedException("ajeno"));

        mockMvc.perform(consulta())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/payments/" + id));
    }

    @Test
    void estadoInvalidoResponde409() throws Exception {
        when(paymentService.getPaymentById(any(), any())).thenThrow(new InvalidPaymentStateException("no se puede"));

        mockMvc.perform(consulta())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("no se puede"));
    }

    @Test
    void montoNoResueltoResponde422() throws Exception {
        when(paymentService.getPaymentById(any(), any())).thenThrow(new ReservationAmountResolutionException("sin monto"));

        mockMvc.perform(consulta())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("sin monto"));
    }
}
