package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.client.FlightCatalogClient;
import com.despescar.koiiaservice.client.HotelCatalogClient;
import com.despescar.koiiaservice.client.PackageCatalogClient;
import com.despescar.koiiaservice.client.dto.AirportResponse;
import com.despescar.koiiaservice.client.dto.FlightResponse;
import com.despescar.koiiaservice.client.dto.HotelResponse;
import com.despescar.koiiaservice.client.dto.TravelPackageResponse;
import com.despescar.koiiaservice.dto.request.KoiConversationMessageRequest;
import com.despescar.koiiaservice.dto.response.KoiConversationResponse;
import com.despescar.koiiaservice.dto.response.KoiRecommendationResponse;
import com.despescar.koiiaservice.entity.KoiConversationMessage;
import com.despescar.koiiaservice.entity.KoiConversationSession;
import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MessageRole;
import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.UserIntent;
import com.despescar.koiiaservice.repository.KoiConversationMessageRepository;
import com.despescar.koiiaservice.repository.KoiConversationSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoiConversationServiceTest {

    @Mock
    private KoiConversationSessionRepository sessionRepository;

    @Mock
    private KoiConversationMessageRepository messageRepository;

    @Mock
    private PackageCatalogClient packageCatalogClient;

    @Mock
    private FlightCatalogClient flightCatalogClient;

    @Mock
    private HotelCatalogClient hotelCatalogClient;

    @InjectMocks
    private KoiConversationService koiConversationService;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
    }

    @Test
    void shouldStartConversationWithFriendlyGreeting() {
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> {
            KoiConversationSession session = invocation.getArgument(0);
            session.setId(sessionId);
            return session;
        });
        when(messageRepository.save(any(KoiConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KoiConversationResponse response = koiConversationService.startSession();

        assertEquals(sessionId, response.getSessionId());
        assertFalse(response.isNeedsMoreInfo());
        assertEquals("¡Hola! Soy KOI ✨\n¿En qué puedo ayudarte hoy?\nContame qué tipo de viaje querés armar y te voy guiando paso a paso.", response.getReply());
        assertTrue(response.getRecommendations().isEmpty());
        verify(messageRepository).save(any(KoiConversationMessage.class));
    }

    @Test
    void shouldAskForMissingBudgetWhenConversationAlreadyHasOtherData() {
        KoiConversationSession session = baseSession();
        session.setTravelers(2);
        session.setDestination("Bariloche");
        session.setTravelMonth("Julio");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(KoiConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KoiConversationResponse response = koiConversationService.handleMessage(
                sessionId,
                message("Queremos seguir buscando opciones para ese viaje")
        );

        assertTrue(response.isNeedsMoreInfo());
        assertEquals(MissingInfoField.BUDGET, response.getNextQuestion());
        assertEquals(List.of(MissingInfoField.BUDGET), response.getMissingFields());
        assertTrue(response.getReply().toLowerCase().contains("presupuesto"));
    }

    @Test
    void shouldReturnPackageRecommendationWhenCatalogHasMatchingPackage() {
        KoiConversationSession session = baseSession();
        session.setIntent(UserIntent.VACATION);
        session.setBudget(BigDecimal.valueOf(1500000));
        session.setTravelers(2);
        session.setDestination("Mendoza");
        session.setTravelMonth("Enero");
        session.setStage(ConversationStage.COLLECTING_INFO);

        TravelPackageResponse travelPackage = new TravelPackageResponse();
        travelPackage.setId(1L);
        travelPackage.setName("Pack Mendoza");
        travelPackage.setDescription("Escapada romántica");
        travelPackage.setDestination("Mendoza");
        travelPackage.setFlightNumber("AR1234");
        travelPackage.setHotelId(UUID.randomUUID());
        travelPackage.setDurationNights(4);
        travelPackage.setBasePrice(BigDecimal.valueOf(1200000));
        travelPackage.setActive(true);

        FlightResponse flight = new FlightResponse();
        flight.setFlightNumber("AR1234");
        flight.setPrice(BigDecimal.valueOf(500000));
        flight.setAvailableSeats(12);
        flight.setDepartureTime(LocalDateTime.of(2026, 1, 10, 10, 0));
        flight.setArrivalTime(LocalDateTime.of(2026, 1, 10, 12, 0));
        AirportResponse destinationAirport = new AirportResponse();
        destinationAirport.setCity("Mendoza");
        flight.setDestinationAirport(destinationAirport);

        HotelResponse hotel = new HotelResponse();
        hotel.setNombre("Hotel Andes");
        hotel.setCiudad("Mendoza");
        hotel.setEstrellas(4);
        hotel.setPrecioPorNoche(90000);
        hotel.setHabitacionesDisponibles(8);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(KoiConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(packageCatalogClient.searchPackages("Mendoza", BigDecimal.valueOf(1500000))).thenReturn(List.of(travelPackage));
        when(flightCatalogClient.findByNumber("AR1234")).thenReturn(flight);
        when(hotelCatalogClient.findById(travelPackage.getHotelId())).thenReturn(hotel);

        KoiConversationResponse response = koiConversationService.handleMessage(
                sessionId,
                message("Continuemos")
        );

        assertFalse(response.isNeedsMoreInfo());
        assertEquals(1, response.getRecommendations().size());
        KoiRecommendationResponse recommendation = response.getRecommendations().get(0);
        assertEquals("PACKAGE", recommendation.getType());
        assertEquals("Pack Mendoza", recommendation.getTitle());
        assertEquals(BigDecimal.valueOf(1200000), recommendation.getPrice());
        assertTrue(response.getReply().contains("opciones"));
        assertEquals(ConversationStage.READY_TO_RECOMMEND, response.getStage());
    }

    private KoiConversationSession baseSession() {
        KoiConversationSession session = new KoiConversationSession();
        session.setId(sessionId);
        session.setStage(ConversationStage.COLLECTING_INFO);
        session.setIntent(UserIntent.UNKNOWN);
        return session;
    }

    private KoiConversationMessageRequest message(String content) {
        KoiConversationMessageRequest request = new KoiConversationMessageRequest();
        request.setMessage(content);
        return request;
    }
}
