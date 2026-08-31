package com.despescar.reservationservice.service;

import com.despescar.reservationservice.client.FlightClient;
import com.despescar.reservationservice.client.HotelClient;
import com.despescar.reservationservice.client.PackageClient;
import com.despescar.reservationservice.dto.flight.response.FlightLookupResponse;
import com.despescar.reservationservice.dto.hotel.response.HotelLookupResponse;
import com.despescar.reservationservice.dto.packagecatalog.response.PackageLookupResponse;
import com.despescar.reservationservice.dto.passengers.request.PassengerRequest;
import com.despescar.reservationservice.dto.reservation.request.CreateReservationRequest;
import com.despescar.reservationservice.dto.reservation.response.ReservationResponse;
import com.despescar.reservationservice.entity.Reservation;
import com.despescar.reservationservice.entity.ReservationDetail;
import com.despescar.reservationservice.enums.ReservationState;
import com.despescar.reservationservice.mapper.ExtraBaggageMapper;
import com.despescar.reservationservice.mapper.ReservationMapper;
import com.despescar.reservationservice.repository.BookingDetailRepository;
import com.despescar.reservationservice.repository.BookingRepository;
import com.despescar.reservationservice.repository.ExtraBaggageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServicePackageSelectionTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingDetailRepository detailRepository;

    @Mock
    private ExtraBaggageRepository extraBaggageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private FlightClient flightClient;

    @Mock
    private HotelClient hotelClient;

    @Mock
    private PackageClient packageClient;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                detailRepository,
                extraBaggageRepository,
                messagingTemplate,
                reservationMapper,
                flightClient,
                hotelClient,
                packageClient
        );
    }

    @Test
    void crearReserva_conPaqueteCompletaVueloHotelYPackageId() {
        Long creatorId = 10L;
        Long packageId = 77L;
        UUID hotelId = UUID.randomUUID();

        CreateReservationRequest request = new CreateReservationRequest();
        request.setCreadorId(creatorId);
        request.setPackageId(packageId);
        request.setAsientos(List.of(new CreateReservationRequest.AsientoSeleccionadoDTO()));
        request.getAsientos().get(0).setNumeroAsiento("12A");
        request.getAsientos().get(0).setUsuarioId(20L);
        request.getAsientos().get(0).setPagadorId(30L);
        request.getAsientos().get(0).setPasajero(new PassengerRequest());

        PackageLookupResponse packageResponse = new PackageLookupResponse();
        packageResponse.setId(packageId);
        packageResponse.setActive(true);
        packageResponse.setFlightNumber("AR123");
        packageResponse.setHotelId(hotelId);

        FlightLookupResponse flightResponse = new FlightLookupResponse();
        flightResponse.setFlightNumber("AR123");
        flightResponse.setPrice(BigDecimal.valueOf(200));
        flightResponse.setStatus("SCHEDULED");

        HotelLookupResponse hotelResponse = new HotelLookupResponse();
        hotelResponse.setId(hotelId);
        hotelResponse.setHabitacionesDisponibles(5);

        when(packageClient.getPackageById(eq(packageId), eq("Bearer token"))).thenReturn(packageResponse);
        when(flightClient.getFlightByNumber("AR123")).thenReturn(flightResponse);
        when(hotelClient.getHotelById(hotelId)).thenReturn(hotelResponse);
        when(bookingRepository.findByEstado(ReservationState.PENDIENTE)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(detailRepository.save(any(ReservationDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationMapper.toResponse(any(Reservation.class))).thenReturn(
                ReservationResponse.builder().idCarrito(1L).build()
        );

        ReservationResponse response = bookingService.crearReserva(request, "Bearer token");

        assertNotNull(response);
        assertEquals(1L, response.getIdCarrito());

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationMapper).toResponse(reservationCaptor.capture());

        Reservation savedReservation = reservationCaptor.getValue();
        assertEquals(packageId, savedReservation.getPackageId());
        assertEquals("AR123", savedReservation.getVueloCodigo());
        assertEquals(hotelId, savedReservation.getHotelId());
    }
}
