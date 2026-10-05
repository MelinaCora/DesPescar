package com.despescar.reservationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.reservationservice.config.SecurityConfig;
import com.despescar.reservationservice.dto.reservation.response.ConfirmacionPagoResponse;
import com.despescar.reservationservice.service.BookingService;
import com.despescar.reservationservice.service.CarritoService;
import com.despescar.reservationservice.service.PassengerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Errores con el formato del servicio {codigo, mensaje, timestamp} (D24, contrato C4). */
@WebMvcTest({CarritoController.class, BookingController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=" + ErroresHttpTest.SECRET,
        "reservation-service.sync-token=" + ErroresHttpTest.TOKEN})
class ErroresHttpTest {

    static final String SECRET = "test-secret-key-for-reservation-errores-1234567890";
    static final String TOKEN = "token-de-payment";
    private static final String INTERNO = "X-Internal-Service-Token";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CarritoService carritoService;
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private PassengerService passengerService;

    private static String cliente() {
        return CarritoControllerTest.jwt("USER", 7L, SECRET);
    }

    @Test
    void sinTokenResponde401ConCodigo() throws Exception {
        mockMvc.perform(get("/api/bookings/carrito"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void unTokenInvalidoLoRechazaCommonSecurity() throws Exception {
        mockMvc.perform(get("/api/bookings/carrito").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void unRolSinPermisoResponde403ConCodigo() throws Exception {
        mockMvc.perform(get("/api/bookings/carrito").header("Authorization", CarritoControllerTest.jwt("HOTEL_ADMIN", 7L, SECRET)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        verify(carritoService, never()).obtenerCarrito(anyLong());
    }

    @Test
    void unCuerpoInvalidoResponde400Validacion() throws Exception {
        mockMvc.perform(post("/api/bookings/carrito/estadias").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void unJsonRotoResponde400Validacion() throws Exception {
        mockMvc.perform(post("/api/bookings/init").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void unIdQueNoEsNumeroResponde400() throws Exception {
        mockMvc.perform(get("/api/bookings/abc").header("Authorization", cliente()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void metodoNoPermitidoResponde405() throws Exception {
        mockMvc.perform(patch("/api/bookings/carrito").header("Authorization", cliente()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void tipoDeContenidoNoSoportadoResponde415() throws Exception {
        mockMvc.perform(post("/api/bookings/carrito/estadias").header("Authorization", cliente())
                        .contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
    }

    @Test
    void rutaInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/bookings/12/otra/cosa").header("Authorization", cliente()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void unErrorInesperadoResponde500SinDetalles() throws Exception {
        when(carritoService.obtenerCarrito(7L)).thenThrow(new IllegalStateException("detalle interno"));

        mockMvc.perform(get("/api/bookings/carrito").header("Authorization", cliente()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un error inesperado."));
    }

    @Test
    void laConfirmacionInternaSinTokenResponde401() throws Exception {
        mockMvc.perform(post("/api/bookings/internal/12/payment-confirmed").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagadorId\":7,\"tokenPago\":\"MOCK-1\",\"monto\":1060000.00}"))
                .andExpect(status().isUnauthorized());
        verify(bookingService, never()).confirmarPago(anyLong(), any());
    }

    @Test
    void laConfirmacionInternaSinMontoResponde400() throws Exception {
        mockMvc.perform(post("/api/bookings/internal/12/payment-confirmed").header(INTERNO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pagadorId\":7,\"tokenPago\":\"MOCK-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verify(bookingService, never()).confirmarPago(anyLong(), any());
    }

    @Test
    void laConfirmacionInternaRespondeJson() throws Exception {
        when(bookingService.confirmarPago(eq(12L), any())).thenReturn(ConfirmacionPagoResponse.confirmada());

        mockMvc.perform(post("/api/bookings/internal/12/payment-confirmed").header(INTERNO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagadorId\":7,\"tokenPago\":\"MOCK-1\",\"monto\":1060000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.motivo").isEmpty())
                .andExpect(jsonPath("$.mensaje").value("Reserva confirmada."));
    }

    @Test
    void unCarritoModificadoEnParaleloResponde409() throws Exception {
        when(carritoService.obtenerCarrito(7L)).thenThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        com.despescar.reservationservice.entity.Reservation.class, 12L));

        mockMvc.perform(get("/api/bookings/carrito").header("Authorization", cliente()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CARRITO_MODIFICADO"))
                .andExpect(jsonPath("$.mensaje").value("Tu carrito cambió mientras guardábamos. Actualizá y probá de nuevo."));
    }

    @Test
    void unaEsperaDeLockOUnDeadlockResponde409OperacionEnCurso() throws Exception {
        when(carritoService.obtenerCarrito(7L))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("Lock wait timeout exceeded; SQL [select ...]"))
                .thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("Deadlock found", null));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/bookings/carrito").header("Authorization", cliente()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("OPERACION_EN_CURSO"))
                    .andExpect(jsonPath("$.mensaje", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
        }
    }

    @Test
    void unaViolacionDeIntegridadResponde409SinDetalleSql() throws Exception {
        when(carritoService.obtenerCarrito(7L)).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("Duplicate entry 'x' for key 'uk_secreto'"));

        mockMvc.perform(get("/api/bookings/carrito").header("Authorization", cliente()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("uk_secreto"))));
    }

    @Test
    void elErrorInternoNoFiltraElMensajeDeLaExcepcion() throws Exception {
        when(carritoService.obtenerCarrito(7L)).thenThrow(new IllegalStateException("select * from reservation"));

        mockMvc.perform(get("/api/bookings/carrito").header("Authorization", cliente()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("select"))));
    }
}
