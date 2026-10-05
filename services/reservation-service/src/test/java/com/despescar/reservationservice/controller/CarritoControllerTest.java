package com.despescar.reservationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.common.security.JwtService;
import com.despescar.reservationservice.config.SecurityConfig;
import com.despescar.reservationservice.dto.carrito.AgregarEstadiaRequest;
import com.despescar.reservationservice.dto.carrito.TitularRequest;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.service.BookingService;
import com.despescar.reservationservice.service.CarritoService;
import com.despescar.reservationservice.service.PassengerService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({CarritoController.class, BookingController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=" + CarritoControllerTest.SECRET)
class CarritoControllerTest {

    static final String SECRET = "test-secret-key-for-reservation-carrito-1234567890";
    private static final String ESTADIA = """
            {"hotelId":"3f2b8c1e-0000-0000-0000-000000000001","tipoHabitacionId":"3f2b8c1e-0000-0000-0000-000000000002",
             "checkIn":"2026-11-10","checkOut":"2026-11-12","cantidadHabitaciones":2,"huespedes":3}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CarritoService carritoService;
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private PassengerService passengerService;

    static String jwt(String rol, long userId, String secreto) {
        return "Bearer " + Jwts.builder()
                .subject("usuario" + userId + "@mail.com")
                .claim("role", rol)
                .claim("userId", userId)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String cliente() {
        return jwt("USER", 7L, SECRET);
    }

    private static ReservationResponse carrito() {
        return ReservationResponse.builder().idCarrito(12L).creadorId(7L).estadoGeneral(ReservationStatus.INICIADA)
                .segundosRestantes(600L).montoTotal(new BigDecimal("580000.00")).moneda("ARS").cantidadItems(1)
                .datosCompletos(false).estadias(List.of()).asientos(List.of()).build();
    }

    @Test
    void sinCarritoResponde204() throws Exception {
        when(carritoService.obtenerCarrito(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/bookings/carrito").header("Authorization", cliente()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void conCarritoDevuelveElDelUsuarioDelToken() throws Exception {
        when(carritoService.obtenerCarrito(7L)).thenReturn(Optional.of(carrito()));

        mockMvc.perform(get("/api/bookings/carrito").header("Authorization", cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCarrito").value(12))
                .andExpect(jsonPath("$.moneda").value("ARS"))
                .andExpect(jsonPath("$.segundosRestantes").value(600));
    }

    @Test
    void agregarUnaEstadiaResponde201() throws Exception {
        when(carritoService.agregarEstadia(any(), eq(7L))).thenReturn(carrito());

        mockMvc.perform(post("/api/bookings/carrito/estadias").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content(ESTADIA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCarrito").value(12));

        ArgumentCaptor<AgregarEstadiaRequest> captor = ArgumentCaptor.forClass(AgregarEstadiaRequest.class);
        verify(carritoService).agregarEstadia(captor.capture(), eq(7L));
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2026, 11, 12), captor.getValue().checkOut());
        org.junit.jupiter.api.Assertions.assertEquals(2, captor.getValue().cantidadHabitaciones());
    }

    @Test
    void unaEstadiaConCeroHabitacionesNoLlegaAlServicio() throws Exception {
        mockMvc.perform(post("/api/bookings/carrito/estadias").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content(ESTADIA.replace("\"cantidadHabitaciones\":2", "\"cantidadHabitaciones\":0")))
                .andExpect(status().isBadRequest());
        verify(carritoService, never()).agregarEstadia(any(), anyLong());
    }

    @Test
    void sinLugarEnElHotelResponde409ConCodigo() throws Exception {
        when(carritoService.agregarEstadia(any(), eq(7L))).thenThrow(new BookingException("SIN_DISPONIBILIDAD_HOTEL",
                "No quedan habitaciones de ese tipo para esas fechas.", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/bookings/carrito/estadias").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content(ESTADIA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_DISPONIBILIDAD_HOTEL"))
                .andExpect(jsonPath("$.mensaje").value("No quedan habitaciones de ese tipo para esas fechas."));
    }

    @Test
    void quitarLaUltimaEstadiaResponde204YSiQuedaAlgo200() throws Exception {
        when(carritoService.quitarEstadia(3L, 7L)).thenReturn(Optional.empty());
        when(carritoService.quitarEstadia(4L, 7L)).thenReturn(Optional.of(carrito()));

        mockMvc.perform(delete("/api/bookings/carrito/estadias/3").header("Authorization", cliente()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/bookings/carrito/estadias/4").header("Authorization", cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCarrito").value(12));
    }

    @Test
    void quitarElVueloSinVueloResponde404() throws Exception {
        when(carritoService.quitarVuelo(7L)).thenThrow(new BookingException("SIN_VUELO", "El carrito no tiene vuelo.", HttpStatus.NOT_FOUND));

        mockMvc.perform(delete("/api/bookings/carrito/vuelo").header("Authorization", cliente()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_VUELO"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void losTitularesLleganComoLista() throws Exception {
        when(carritoService.cargarTitulares(eq(12L), any(), eq(7L))).thenReturn(carrito());

        mockMvc.perform(put("/api/bookings/12/titulares").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"estadiaId\":3,\"nombre\":\"Ana Pérez\",\"dni\":\"30111222\",\"telefono\":\"+54 11 5555-5555\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCarrito").value(12));

        ArgumentCaptor<List<TitularRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(carritoService).cargarTitulares(eq(12L), captor.capture(), eq(7L));
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(new TitularRequest(3L, "Ana Pérez", "30111222", "+54 11 5555-5555")), captor.getValue());
    }

    @Test
    void abandonarUsaElUsuarioDelToken() throws Exception {
        mockMvc.perform(delete("/api/bookings/12").header("Authorization", cliente()))
                .andExpect(status().isOk());
        verify(carritoService).abandonar(12L, 7L);
    }

    @Test
    void unaReservaPagadaNoSeAbandona() throws Exception {
        org.mockito.Mockito.doThrow(new BookingException("USAR_CANCELACION_POR_ITEM", "Cancelá por ítem.", HttpStatus.CONFLICT))
                .when(carritoService).abandonar(12L, 7L);

        mockMvc.perform(delete("/api/bookings/12").header("Authorization", cliente()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USAR_CANCELACION_POR_ITEM"));
    }

    @Test
    void sinTokenNoLlegaAlServicio() throws Exception {
        // Hoy Spring Security responde 403 sin cuerpo (no hay entry point); R8 lo pasa a 401 NO_AUTENTICADO
        mockMvc.perform(get("/api/bookings/carrito")).andExpect(status().is4xxClientError());
        verify(carritoService, never()).obtenerCarrito(anyLong());
    }

    @Test
    void unRolQueNoEsClienteResponde403() throws Exception {
        mockMvc.perform(post("/api/bookings/carrito/estadias").header("Authorization", jwt("HOTEL_ADMIN", 7L, SECRET))
                        .contentType(MediaType.APPLICATION_JSON).content(ESTADIA))
                .andExpect(status().isForbidden());
        verify(carritoService, never()).agregarEstadia(any(), anyLong());
    }
}
