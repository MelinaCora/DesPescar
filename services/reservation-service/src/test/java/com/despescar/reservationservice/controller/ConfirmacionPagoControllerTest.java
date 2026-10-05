package com.despescar.reservationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.reservationservice.config.SecurityConfig;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.service.BookingService;
import com.despescar.reservationservice.service.CarritoService;
import com.despescar.reservationservice.service.PassengerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** payment-confirmed (contrato C3): payment-service decide el reembolso según estos códigos exactos. */
@WebMvcTest({CarritoController.class, BookingController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=" + ConfirmacionPagoControllerTest.SECRET,
        "reservation-service.sync-token=" + ConfirmacionPagoControllerTest.TOKEN})
class ConfirmacionPagoControllerTest {

    static final String SECRET = "test-secret-key-for-reservation-confirmacion-123456";
    static final String TOKEN = "token-de-payment";
    private static final String INTERNO = "X-Internal-Service-Token";
    private static final String URL = "/api/bookings/internal/12/payment-confirmed";
    private static final String CUERPO = "{\"pagadorId\":7,\"tokenPago\":\"MOCK-1\",\"monto\":1060000.00}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CarritoService carritoService;
    @MockitoBean
    private com.despescar.reservationservice.service.CancelacionService cancelacionService;
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private PassengerService passengerService;

    @Test
    void respondeJsonConEstadoMotivoYMensaje() throws Exception {
        when(bookingService.confirmarPago(eq(12L), any())).thenReturn(ConfirmacionPagoResponse.confirmada());

        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.motivo").isEmpty())
                .andExpect(jsonPath("$.mensaje").value("Reserva confirmada."));
    }

    @Test
    void unPagoDuplicadoResponde200Rechazada() throws Exception {
        when(bookingService.confirmarPago(eq(12L), any())).thenReturn(ConfirmacionPagoResponse.rechazada(
                ConfirmacionPagoResponse.PAGO_DUPLICADO, "La reserva ya fue pagada con otro pago."));

        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivo").value("PAGO_DUPLICADO"));
    }

    @Test
    void sinMontoResponde400Validacion() throws Exception {
        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagadorId\":7,\"tokenPago\":\"MOCK-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        verify(bookingService, never()).confirmarPago(anyLong(), any());
    }

    @Test
    void unMontoNoPositivoResponde400Validacion() throws Exception {
        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagadorId\":7,\"tokenPago\":\"MOCK-1\",\"monto\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void unJsonRotoResponde400Validacion() throws Exception {
        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verify(bookingService, never()).confirmarPago(anyLong(), any());
    }

    @Test
    void unPagadorQueNoEsElCreadorResponde400PagadorInvalido() throws Exception {
        when(bookingService.confirmarPago(eq(12L), any())).thenThrow(new BookingException(
                "PAGADOR_INVALIDO", "El pagador no es el creador de la reserva.", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAGADOR_INVALIDO"));
    }

    @Test
    void unaReservaInexistenteResponde404ConCodigo() throws Exception {
        when(bookingService.confirmarPago(eq(12L), any())).thenThrow(new BookingException(
                "RESERVA_NO_ENCONTRADA", "La reserva no existe.", HttpStatus.NOT_FOUND));

        mockMvc.perform(post(URL).header(INTERNO, TOKEN).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_ENCONTRADA"));
    }
}
