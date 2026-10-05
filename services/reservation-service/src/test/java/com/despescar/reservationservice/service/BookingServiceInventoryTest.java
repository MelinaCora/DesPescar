package com.despescar.reservationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.PackageClient;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.PaymentStatus;
import com.despescar.reservationservice.enums.ReservationStatus;
import com.despescar.reservationservice.exception.BookingException;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.BookingRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** El inventario de vuelos se descuenta al confirmar el pago y se devuelve al cancelar una confirmada. */
@ExtendWith(MockitoExtension.class)
class BookingServiceInventoryTest {

    private static final UUID FLIGHT_ID = UUID.randomUUID();

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingDetailRepository detailRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private FlightClient flightClient;
    @Mock
    private PackageClient packageClient;

    @InjectMocks
    private BookingService bookingService;

    private Reservation reserva;

    @BeforeEach
    void setUp() {
        reserva = Reservation.builder()
                .id(7L)
                .creadorId(1L)
                .cantidadPasajeros(2)
                .flightIds(new ArrayList<>(List.of(FLIGHT_ID)))
                .estado(ReservationStatus.PENDIENTE_PAGO)
                .limiteTiempo(LocalDateTime.now().plusMinutes(10))
                .build();
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(reserva));
    }

    private void vueloConNumero(String number) {
        FlightLookupResponse vuelo = new FlightLookupResponse();
        vuelo.setId(FLIGHT_ID);
        vuelo.setFlightNumber(number);
        when(flightClient.getFlightByNumber(FLIGHT_ID)).thenReturn(vuelo);
    }

    private ReservationDetail detallePendiente() {
        return ReservationDetail.builder()
                .passengerName("Ana Perez")
                .passengerDni("30111222")
                .payerUserId(1L)
                .paymentStatus(PaymentStatus.PENDIENTE)
                .outboundSeatNumber("12A")
                .build();
    }

    @Test
    void alConfirmarElPagoDescuentaAsientosPorNumeroDeVuelo() {
        vueloConNumero("AR1234");
        when(detailRepository.findByReservation_IdAndPayerUserIdAndPaymentStatus(7L, 1L, PaymentStatus.PENDIENTE))
                .thenReturn(List.of(detallePendiente(), detallePendiente()));
        when(detailRepository.countByReservation_IdAndPaymentStatus(7L, PaymentStatus.PENDIENTE)).thenReturn(0L);

        String mensaje = bookingService.confirmarPagoValidado(7L, 1L, "mp-123");

        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        assertEquals("Reserva confirmada. Todos los pagos fueron realizados.", mensaje);
        verify(flightClient).adjustSeats("AR1234", -2);
    }

    @Test
    void siFaltanPagadoresNoDescuentaTodavia() {
        when(detailRepository.findByReservation_IdAndPayerUserIdAndPaymentStatus(7L, 1L, PaymentStatus.PENDIENTE))
                .thenReturn(List.of(detallePendiente()));
        when(detailRepository.countByReservation_IdAndPaymentStatus(7L, PaymentStatus.PENDIENTE)).thenReturn(1L);

        bookingService.confirmarPagoValidado(7L, 1L, "mp-123");

        assertEquals(ReservationStatus.PENDIENTE_PAGO, reserva.getEstado());
        verify(flightClient, never()).adjustSeats(anyString(), anyInt());
    }

    @Test
    void cancelarUnaReservaConfirmadaDevuelveAsientos() {
        reserva.setEstado(ReservationStatus.CONFIRMADA);
        vueloConNumero("AR1234");
        when(detailRepository.findByReservation_Id(7L)).thenReturn(List.of());

        bookingService.cancelarReservaManualmente(7L, 1L);

        assertEquals(ReservationStatus.CANCELADA, reserva.getEstado());
        verify(flightClient).adjustSeats("AR1234", 2);
    }

    @Test
    void cancelarUnaReservaSinPagarNoTocaElInventario() {
        when(detailRepository.findByReservation_Id(7L)).thenReturn(List.of());

        bookingService.cancelarReservaManualmente(7L, 1L);

        assertEquals(ReservationStatus.CANCELADA, reserva.getEstado());
        verify(flightClient, never()).adjustSeats(anyString(), anyInt());
    }

    @Test
    void soloElCreadorPuedeCancelarYNoSeTocaElInventario() {
        reserva.setEstado(ReservationStatus.CONFIRMADA);

        assertThrows(BookingException.class, () -> bookingService.cancelarReservaManualmente(7L, 99L));

        assertEquals(ReservationStatus.CONFIRMADA, reserva.getEstado());
        verify(flightClient, never()).adjustSeats(anyString(), anyInt());
    }
}
