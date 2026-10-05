package com.despescar.reservationservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.dto.hotel.RetencionHotelResponse;
import com.despescar.reservationservice.entity.EstadiaHotel;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.entity.Seat;
import com.despescar.reservationservice.entity.TramoPolitica;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.PaymentType;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.SeatRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La confirmación del pago entera, por HTTP y contra la base: lee la reserva, llama al hotel sin
 * transacción y después la relee bloqueada. Esa relectura tiene que traer lo que hay en la base y no
 * la copia de la primera lectura; con una sesión abierta durante todo el pedido (open-in-view)
 * devolvía la misma copia y no veía lo que otro pedido había cambiado mientras tanto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConfirmacionPagoIntegracionTest {

    private static final UUID VUELO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private TransactionTemplate transaccion;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;
    @Value("${reservation-service.sync-token}")
    private String tokenInterno;
    @MockitoBean
    private HotelClient hotelClient;
    @MockitoBean
    private FlightClient flightClient;

    @AfterEach
    void limpiar() {
        bookingRepository.deleteAll();
        seatRepository.deleteAll();
    }

    private Reservation carrito(int estadias) {
        return transaccion.execute(estado -> {
            Reservation r = Reservation.builder().creadorId(7L).cantidadPasajeros(1)
                    .tipoPago(PaymentType.SINGLE_PAYMENT).estado(ReservationStatus.PENDIENTE_PAGO)
                    .limiteTiempo(LocalDateTime.now(clock).plusMinutes(10))
                    .flightIds(new ArrayList<>(List.of(VUELO)))
                    .precioVueloPorPasajero(new BigDecimal("240000.00")).build();
            r.getDetalles().add(ReservationDetail.builder().reservation(r).passengerName("Ana Pérez")
                    .passengerDni("30111222").priceCharged(new BigDecimal("240000.00")).fareCurrency("ARS")
                    .outboundSeatNumber("1A").payerUserId(7L).paymentStatus(PaymentStatus.PENDIENTE).build());
            for (int i = 0; i < estadias; i++) {
                EstadiaHotel e = new EstadiaHotel();
                e.setReservation(r);
                e.setHotelId(UUID.randomUUID());
                e.setHotelNombre("Sheraton Córdoba");
                e.setCiudad("Córdoba");
                e.setTipoHabitacionId(UUID.randomUUID());
                e.setTipoHabitacionNombre("Doble");
                e.setCheckIn(LocalDate.of(2026, 11, 10));
                e.setCheckOut(LocalDate.of(2026, 11, 12));
                e.setCantidadHabitaciones(1);
                e.setHuespedes(2);
                e.setRetencionId(UUID.randomUUID());
                e.setPrecioTotal(new BigDecimal("100000.00"));
                e.setMoneda("ARS");
                e.setPoliticaCancelacion(new ArrayList<>(List.of(new TramoPolitica(48, 100))));
                e.setHoraCheckIn(LocalTime.of(14, 0));
                e.setZonaHoraria("America/Argentina/Buenos_Aires");
                e.setTitularNombre("Ana Pérez");
                e.setTitularDni("30111222");
                e.setTitularTelefono("1155555555");
                r.getEstadias().add(e);
            }
            Reservation guardada = bookingRepository.saveAndFlush(r);
            Seat s = new Seat();
            s.setFlightId(VUELO);
            s.setNumberSeat("1A");
            s.setStatusSeat("RESERVADO_TEMPORAL");
            s.setBlockedByUserId(7L);
            s.setBloqueadoHasta(guardada.getLimiteTiempo());
            s.setReservaId(guardada.getId());
            seatRepository.save(s);
            guardada.getEstadias().forEach(e -> e.getRetencionId()); // quedan cargadas para el test
            return guardada;
        });
    }

    private void vueloConNumero() {
        FlightLookupResponse vuelo = new FlightLookupResponse();
        vuelo.setFlightNumber("AR1234");
        when(flightClient.getFlightByNumber(VUELO)).thenReturn(vuelo);
    }

    private ResultActions pagar(Long id, String monto) throws Exception {
        return mockMvc.perform(post("/api/bookings/internal/" + id + "/payment-confirmed")
                .header("X-Internal-Service-Token", tokenInterno).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pagadorId\":7,\"tokenPago\":\"MOCK-1\",\"monto\":" + monto + "}"));
    }

    private String estadoEnLaBase(Long id) {
        return jdbc.queryForObject("select estado_reserva from reservations where id = ?", String.class, id);
    }

    @Test
    void confirmaUnCarritoDeVueloYHotel() throws Exception {
        Reservation r = carrito(1);
        vueloConNumero();
        when(hotelClient.confirmarRetencion(any(), any())).thenReturn(new RetencionHotelResponse());

        pagar(r.getId(), "340000.00").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        assertEquals("CONFIRMADA", estadoEnLaBase(r.getId()));
        Seat asiento = seatRepository.findByFlightId(VUELO).get(0);
        assertEquals("OCUPADO", asiento.getStatusSeat());
        assertEquals(r.getId(), asiento.getReservaId());
        verify(hotelClient, never()).liberarRetencion(any());
    }

    @Test
    void siElTitularCambiaMientrasSeLlamaAlHotelSeConfirmaConElVigente() throws Exception {
        Reservation r = carrito(1);
        UUID retencion = r.getEstadias().get(0).getRetencionId();
        vueloConNumero();
        // Mientras se confirma con "Ana Pérez", otro pedido cambia el titular: solo toca la fila de la
        // estadía, así que la versión de la reserva no cambia
        when(hotelClient.confirmarRetencion(retencion, "Ana Pérez")).thenAnswer(inv -> {
            jdbc.update("update estadias_hotel set titular_nombre = 'Beatriz Gómez' where reservation_id = ?", r.getId());
            return new RetencionHotelResponse();
        });
        when(hotelClient.confirmarRetencion(retencion, "Beatriz Gómez")).thenReturn(new RetencionHotelResponse());

        pagar(r.getId(), "340000.00").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        verify(hotelClient).confirmarRetencion(retencion, "Beatriz Gómez");
        assertEquals("Beatriz Gómez", jdbc.queryForObject(
                "select titular_nombre from estadias_hotel where reservation_id = ?", String.class, r.getId()));
    }

    @Test
    void unIntentoSolapadoQueSeQuedaSinLugarNoSueltaLasRetencionesDelQueConfirmo() throws Exception {
        Reservation r = carrito(2);
        UUID primera = r.getEstadias().get(0).getRetencionId();
        UUID segunda = r.getEstadias().get(1).getRetencionId();
        vueloConNumero();
        when(hotelClient.confirmarRetencion(eq(primera), any())).thenReturn(new RetencionHotelResponse());
        // El intento B ya confirmó la primera; mientras espera la segunda, el intento A (el mismo pago,
        // reenviado) confirma todo y commitea. Recién entonces a B le responden "sin lugar".
        AtomicInteger llamadas = new AtomicInteger();
        when(hotelClient.confirmarRetencion(eq(segunda), any())).thenAnswer(inv -> {
            if (llamadas.incrementAndGet() == 1) {
                pagar(r.getId(), "440000.00").andExpect(jsonPath("$.estado").value("CONFIRMADA"));
                throw new BookingException("SIN_DISPONIBILIDAD_HOTEL", "Sin lugar", HttpStatus.CONFLICT);
            }
            return new RetencionHotelResponse();
        });

        pagar(r.getId(), "440000.00").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        assertEquals("CONFIRMADA", estadoEnLaBase(r.getId()));
        assertEquals("OCUPADO", seatRepository.findByFlightId(VUELO).get(0).getStatusSeat());
        verify(hotelClient, never()).liberarRetencion(any());
        assertTrue(llamadas.get() >= 2);
    }
}
