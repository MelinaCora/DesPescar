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
import com.despescar.koiiaservice.service.ai.KoiAiAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
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

    @Mock
    private KoiAiAssistant koiAiAssistant;

    @InjectMocks
    private KoiConversationService koiConversationService;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
    }

    @Test
    void shouldStartConversationWithFriendlyGreeting() {
        when(sessionRepository.findTopByUserIdentifierOrderByUpdatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> {
            KoiConversationSession session = invocation.getArgument(0);
            session.setId(sessionId);
            return session;
        });
        when(messageRepository.save(any(KoiConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KoiConversationResponse response = koiConversationService.startSession("test@example.com");

        assertEquals(sessionId, response.getSessionId());
        assertFalse(response.isNeedsMoreInfo());
        assertEquals("¡Hola! Soy KOI ✨\n¿En qué puedo ayudarte hoy?\nContame qué tipo de viaje querés armar y te voy guiando paso a paso.", response.getReply());
        assertTrue(response.getRecommendations().isEmpty());
        verify(messageRepository).save(any(KoiConversationMessage.class));
    }

    @Test
    void shouldRestorePreferencesFromPreviousSessionForSameUser() {
        KoiConversationSession previous = baseSession();
        previous.setUserIdentifier("test@example.com");
        previous.setPreferredBudget(BigDecimal.valueOf(900000));
        previous.setPreferredDestination("Bariloche");
        previous.setPreferredTravelStyle("PLAYA");
        previous.setPreferredTravelers(2);
        previous.setPreferredSoloTravel(false);
        previous.setPreferredOrigin("Córdoba");
        previous.setPreferredNights(5);
        previous.setPreferredDepartureDate(LocalDate.of(2026, 7, 10));
        previous.setPreferredReturnDate(LocalDate.of(2026, 7, 15));

        when(sessionRepository.findTopByUserIdentifierOrderByUpdatedAtDesc("test@example.com")).thenReturn(Optional.of(previous));
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> {
            KoiConversationSession session = invocation.getArgument(0);
            session.setId(sessionId);
            return session;
        });
        when(messageRepository.save(any(KoiConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KoiConversationResponse response = koiConversationService.startSession("test@example.com");

        ArgumentCaptor<KoiConversationSession> captor = ArgumentCaptor.forClass(KoiConversationSession.class);
        verify(sessionRepository, atLeastOnce()).save(captor.capture());

        KoiConversationSession saved = captor.getAllValues().get(0);
        assertEquals("test@example.com", saved.getUserIdentifier());
        assertEquals(BigDecimal.valueOf(900000), saved.getBudget());
        assertEquals("Bariloche", saved.getDestination());
        assertEquals("PLAYA", saved.getTravelStyle());
        assertEquals(2, saved.getTravelers());
        assertTrue(response.getReply().contains("Retomé tus últimas preferencias para no arrancar de cero."));
        assertTrue(response.getReply().contains("Destino favorito: Bariloche."));
        assertTrue(response.getReply().contains("Presupuesto habitual: $900000."));
        assertTrue(response.getReply().contains("Te gusta viajar en modo playa."));
        assertTrue(response.getReply().contains("Sueles viajar en grupo."));
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
                message("Queremos seguir buscando opciones para ese viaje"),
                "test@example.com"
        );

        assertTrue(response.isNeedsMoreInfo());
        assertEquals(MissingInfoField.BUDGET, response.getNextQuestion());
        assertEquals(MissingInfoField.BUDGET, response.getMissingFields().get(0));
        assertTrue(response.getMissingFields().contains(MissingInfoField.ORIGIN));
        assertTrue(response.getMissingFields().contains(MissingInfoField.TRAVEL_STYLE));
        assertTrue(response.getMissingFields().contains(MissingInfoField.NIGHTS));
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
        session.setOrigin("Córdoba");
        session.setTravelStyle("PLAYA");
        session.setNights(4);
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
                message("Continuemos"),
                "test@example.com"
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

    @Test
    void shouldExtractTravelPreferencesAndUseElegantFallbackWhenNothingMatches() {
        KoiConversationSession session = baseSession();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(KoiConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(packageCatalogClient.searchPackages(anyString(), org.mockito.ArgumentMatchers.any(BigDecimal.class))).thenReturn(List.of());
        when(flightCatalogClient.findAllFlights()).thenReturn(List.of());
        when(hotelCatalogClient.findByCity(anyString())).thenReturn(List.of());

        KoiConversationResponse response = koiConversationService.handleMessage(
                sessionId,
                message("Quiero viajar a Mendoza desde Córdoba, 5 noches, estilo playa, somos 2 y tengo 1500000 para julio"),
                "test@example.com"
        );

        ArgumentCaptor<KoiConversationSession> captor = ArgumentCaptor.forClass(KoiConversationSession.class);
        verify(sessionRepository, atLeastOnce()).save(captor.capture());

        assertTrue(captor.getAllValues().stream().anyMatch(saved ->
                "Mendoza".equals(saved.getDestination())
                        && "Cordoba".equals(saved.getOrigin())
                        && "PLAYA".equals(saved.getTravelStyle())
                        && Integer.valueOf(5).equals(saved.getNights())
                        && Integer.valueOf(2).equals(saved.getTravelers())
                        && BigDecimal.valueOf(1500000).equals(saved.getBudget())
        ));
        assertTrue(response.getReply().contains("No encontré algo exacto"));
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
