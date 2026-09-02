package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.client.FlightCatalogClient;
import com.despescar.koiiaservice.client.HotelCatalogClient;
import com.despescar.koiiaservice.client.PackageCatalogClient;
import com.despescar.koiiaservice.client.dto.FlightResponse;
import com.despescar.koiiaservice.client.dto.HotelResponse;
import com.despescar.koiiaservice.client.dto.TravelPackageResponse;
import com.despescar.koiiaservice.dto.request.KoiConversationMessageRequest;
import com.despescar.koiiaservice.dto.response.KoiConversationResponse;
import com.despescar.koiiaservice.dto.response.KoiRecommendationResponse;
import com.despescar.koiiaservice.dto.response.KoiSessionResponse;
import com.despescar.koiiaservice.entity.KoiConversationMessage;
import com.despescar.koiiaservice.entity.KoiConversationSession;
import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.MessageRole;
import com.despescar.koiiaservice.enums.UserIntent;
import com.despescar.koiiaservice.exception.KoiCatalogUnavailableException;
import com.despescar.koiiaservice.exception.KoiSessionNotFoundException;
import com.despescar.koiiaservice.repository.KoiConversationMessageRepository;
import com.despescar.koiiaservice.repository.KoiConversationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KoiConversationService {

    private final KoiConversationSessionRepository sessionRepository;
    private final KoiConversationMessageRepository messageRepository;
    private final PackageCatalogClient packageCatalogClient;
    private final FlightCatalogClient flightCatalogClient;
    private final HotelCatalogClient hotelCatalogClient;

    @Transactional
    public KoiConversationResponse startSession(String userIdentifier) {
        KoiConversationSession session = new KoiConversationSession();
        session.setUserIdentifier(normalizeUserIdentifier(userIdentifier));
        session.setStage(ConversationStage.COLLECTING_INFO);
        session.setIntent(UserIntent.UNKNOWN);
        seedSessionFromMemory(session);
        session = sessionRepository.save(session);

        String reply = buildGreeting(session);
        saveAssistantMessage(session, reply);
        return toConversationResponse(session, reply, false, null, List.of());
    }

    @Transactional(readOnly = true)
    public KoiSessionResponse getSession(UUID sessionId) {
        return toSessionResponse(loadSession(sessionId));
    }

    @Transactional
    public KoiConversationResponse handleMessage(UUID sessionId, KoiConversationMessageRequest request, String userIdentifier) {
        KoiConversationSession session = loadSession(sessionId);
        ensureSessionOwner(session, userIdentifier);
        if (session.getUserIdentifier() == null && !isBlank(userIdentifier)) {
            session.setUserIdentifier(normalizeUserIdentifier(userIdentifier));
        }
        String message = request.getMessage().trim();

        saveUserMessage(session, message);
        applyUserInput(session, message);
        session = sessionRepository.save(session);

        List<MissingInfoField> missingFields = determineMissingFields(session);
        if (!missingFields.isEmpty()) {
            MissingInfoField nextQuestion = missingFields.get(0);
            String reply = friendlyQuestion(nextQuestion, session);
            session.setStage(ConversationStage.COLLECTING_INFO);
            session.setAwaitingField(nextQuestion);
            session.setLastAssistantMessage(reply);
            session = sessionRepository.save(session);
            saveAssistantMessage(session, reply);
            return toConversationResponse(session, reply, true, nextQuestion, missingFields);
        }

        session.setStage(ConversationStage.RECOMMENDING);
        session.setAwaitingField(null);
        session = sessionRepository.save(session);

        List<KoiRecommendationResponse> recommendations = buildRecommendations(session);
        String reply = composeRecommendationReply(session, recommendations);
        session.setStage(ConversationStage.READY_TO_RECOMMEND);
        session.setLastAssistantMessage(reply);
        session = sessionRepository.save(session);
        saveAssistantMessage(session, reply);

        return toConversationResponse(session, reply, false, null, recommendations);
    }

    private KoiConversationSession loadSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new KoiSessionNotFoundException(sessionId));
    }

    private void ensureSessionOwner(KoiConversationSession session, String userIdentifier) {
        String normalizedUser = normalizeUserIdentifier(userIdentifier);
        if (normalizedUser == null) {
            return;
        }
        if (session.getUserIdentifier() != null && !session.getUserIdentifier().equals(normalizedUser)) {
            throw new IllegalArgumentException("La sesión KOI no pertenece al usuario autenticado.");
        }
    }

    private void seedSessionFromMemory(KoiConversationSession session) {
        String userIdentifier = session.getUserIdentifier();
        if (isBlank(userIdentifier)) {
            return;
        }

        Optional<KoiConversationSession> previousSession = sessionRepository
                .findTopByUserIdentifierOrderByUpdatedAtDesc(userIdentifier);

        if (previousSession.isEmpty()) {
            return;
        }

        KoiConversationSession memory = previousSession.get();
        session.setPreferredBudget(firstNonNull(memory.getPreferredBudget(), memory.getBudget()));
        session.setPreferredTravelers(firstNonNull(memory.getPreferredTravelers(), memory.getTravelers()));
        session.setPreferredDestination(firstNonNull(memory.getPreferredDestination(), memory.getDestination()));
        session.setPreferredOrigin(firstNonNull(memory.getPreferredOrigin(), memory.getOrigin()));
        session.setPreferredTravelStyle(firstNonNull(memory.getPreferredTravelStyle(), memory.getTravelStyle()));
        session.setPreferredNights(firstNonNull(memory.getPreferredNights(), memory.getNights()));
        session.setPreferredSoloTravel(firstNonNull(memory.getPreferredSoloTravel(), memory.getTravelers() != null ? memory.getTravelers() == 1 : null));
        session.setPreferredDepartureDate(firstNonNull(memory.getPreferredDepartureDate(), memory.getDepartureDate()));
        session.setPreferredReturnDate(firstNonNull(memory.getPreferredReturnDate(), memory.getReturnDate()));

        session.setBudget(session.getPreferredBudget());
        session.setTravelers(session.getPreferredTravelers());
        session.setDestination(session.getPreferredDestination());
        session.setOrigin(session.getPreferredOrigin());
        session.setTravelStyle(session.getPreferredTravelStyle());
        session.setNights(session.getPreferredNights());
        session.setDepartureDate(session.getPreferredDepartureDate());
        session.setReturnDate(session.getPreferredReturnDate());

        if (session.getTravelers() != null) {
            session.setPreferredSoloTravel(session.getTravelers() == 1);
        }
    }

    private void applyUserInput(KoiConversationSession session, String message) {
        UserIntent detectedIntent = detectIntent(message);
        if (detectedIntent != UserIntent.UNKNOWN
                && (session.getIntent() == null
                || session.getIntent() == UserIntent.UNKNOWN
                || session.getIntent() == UserIntent.VACATION)) {
            session.setIntent(detectedIntent);
        }

        BigDecimal budget = extractBudget(message);
        if (budget != null) {
            session.setBudget(budget);
            session.setPreferredBudget(budget);
        }

        Integer travelers = extractTravelers(message);
        if (travelers != null) {
            session.setTravelers(travelers);
            session.setPreferredTravelers(travelers);
            session.setPreferredSoloTravel(travelers == 1);
        }

        String destination = extractDestination(message);
        if (destination != null) {
            session.setDestination(destination);
            session.setPreferredDestination(destination);
        }

        String origin = extractOrigin(message);
        if (origin != null) {
            session.setOrigin(origin);
            session.setPreferredOrigin(origin);
        }

        String travelStyle = extractTravelStyle(message);
        if (travelStyle != null) {
            session.setTravelStyle(travelStyle);
            session.setPreferredTravelStyle(travelStyle);
        }

        Integer nights = extractNights(message);
        if (nights != null) {
            session.setNights(nights);
            session.setPreferredNights(nights);
        }

        LocalDate departureDate = extractDepartureDate(message);
        if (departureDate != null) {
            session.setDepartureDate(departureDate);
            session.setPreferredDepartureDate(departureDate);
        }

        LocalDate returnDate = extractReturnDate(message);
        if (returnDate != null) {
            session.setReturnDate(returnDate);
            session.setPreferredReturnDate(returnDate);
        }

        String month = extractMonth(message);
        if (month != null) {
            session.setTravelMonth(month);
        }

        syncPreferenceSnapshot(session);

        if (session.getUserGoal() == null || session.getUserGoal().isBlank()) {
            session.setUserGoal(message);
        } else {
            session.setUserGoal(session.getUserGoal() + " | " + message);
        }
    }

    private UserIntent detectIntent(String message) {
        String normalized = normalize(message);
        if (containsAny(normalized, "vuelo", "vuelos", "avion", "avión", "aerolinea", "aerolínea")) {
            return UserIntent.FLIGHT_SEARCH;
        }
        if (containsAny(normalized, "hotel", "alojamiento", "estadía", "estadia")) {
            return UserIntent.HOTEL_SEARCH;
        }
        if (containsAny(normalized, "paquete", "paquetes")) {
            return UserIntent.PACKAGE_SEARCH;
        }
        if (containsAny(normalized, "vacacion", "viaje", "vacaciones", "escapada", "turismo", "descanso")) {
            return UserIntent.VACATION;
        }
        return UserIntent.UNKNOWN;
    }

    private List<MissingInfoField> determineMissingFields(KoiConversationSession session) {
        List<MissingInfoField> missing = new ArrayList<>();

        if (session.getBudget() == null) {
            missing.add(MissingInfoField.BUDGET);
        }
        if (session.getTravelers() == null) {
            missing.add(MissingInfoField.TRAVELERS);
        }
        if (isBlank(session.getDestination())) {
            missing.add(MissingInfoField.DESTINATION);
        }
        if (isBlank(session.getOrigin())) {
            missing.add(MissingInfoField.ORIGIN);
        }
        if (isBlank(session.getTravelStyle())) {
            missing.add(MissingInfoField.TRAVEL_STYLE);
        }
        if (session.getNights() == null) {
            missing.add(MissingInfoField.NIGHTS);
        }
        if (isBlank(session.getTravelMonth())) {
            missing.add(MissingInfoField.MONTH);
        }

        return missing;
    }

    private String friendlyQuestion(MissingInfoField field, KoiConversationSession session) {
        return switch (field) {
            case BUDGET -> "¡Buenísimo! ¿Con qué presupuesto te gustaría jugar para buscar opciones?";
            case TRAVELERS -> "¿Cuántas personas viajan?";
            case DESTINATION -> "¿Tenés algún destino en mente?";
            case ORIGIN -> "¿Desde qué ciudad o aeropuerto saldrías?";
            case TRAVEL_STYLE -> "¿Qué estilo de viaje te gustaría: playa, ciudad, relax o aventura?";
            case NIGHTS -> "¿Cuántas noches querés viajar?";
            case MONTH -> "¿En qué mes te gustaría viajar?";
        };
    }

    private List<KoiRecommendationResponse> buildRecommendations(KoiConversationSession session) {
        List<KoiRecommendationResponse> recommendations = new ArrayList<>();
        String destination = effectiveDestination(session);
        BigDecimal budget = effectiveBudget(session);

        List<TravelPackageResponse> packages = packageCatalogClient.searchPackages(destination, budget);
        packages.stream()
                .filter(TravelPackageResponse::isActive)
                .filter(pkg -> budget == null
                        || pkg.getBasePrice() == null
                        || pkg.getBasePrice().compareTo(budget) <= 0)
                .filter(pkg -> session.getNights() == null
                        || pkg.getDurationNights() == null
                        || Math.abs(pkg.getDurationNights() - session.getNights()) <= 1)
                .sorted(Comparator.comparing(TravelPackageResponse::getBasePrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(3)
                .forEach(pkg -> recommendations.add(buildPackageRecommendation(session, pkg)));

        if (!recommendations.isEmpty()) {
            return recommendations;
        }

        List<FlightResponse> flights = flightCatalogClient.findAllFlights();
        flights.stream()
                .filter(flight -> flight.getDestinationAirport() != null)
                .filter(flight -> destination == null
                        || matchesDestination(flight.getDestinationAirport().getCity(), destination)
                        || matchesDestination(flight.getDestinationAirport().getCountry(), destination)
                        || matchesDestination(flight.getDestinationAirport().getName(), destination))
                .filter(flight -> isBlank(session.getOrigin())
                        || (flight.getOriginAirport() != null && (
                        matchesDestination(flight.getOriginAirport().getCity(), session.getOrigin())
                                || matchesDestination(flight.getOriginAirport().getCountry(), session.getOrigin())
                                || matchesDestination(flight.getOriginAirport().getName(), session.getOrigin()))))
                .filter(flight -> flight.getAvailableSeats() == null || flight.getAvailableSeats() > 0)
                .filter(flight -> budget == null || flight.getPrice() == null
                        || flight.getPrice().compareTo(perPersonBudget(session, flight.getPrice())) <= 0)
                .filter(flight -> session.getDepartureDate() == null
                        || (flight.getDepartureTime() != null
                        && flight.getDepartureTime().toLocalDate().equals(session.getDepartureDate())))
                .filter(flight -> session.getTravelMonth() == null
                        || (flight.getDepartureTime() != null
                        && matchesMonth(flight.getDepartureTime().toLocalDate().getMonthValue(), session.getTravelMonth())))
                .sorted(Comparator.comparing(FlightResponse::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(2)
                .forEach(flight -> recommendations.add(buildFlightRecommendation(session, flight)));

        List<HotelResponse> hotels = isBlank(destination) ? List.of() : hotelCatalogClient.findByCity(destination);
        hotels.stream()
                .filter(hotel -> hotel.getHabitacionesDisponibles() > 0)
                .filter(hotel -> matchesHotelStyle(session.getTravelStyle(), hotel))
                .sorted(Comparator.comparingDouble(HotelResponse::getPrecioPorNoche))
                .limit(2)
                .forEach(hotel -> recommendations.add(buildHotelRecommendation(session, hotel)));

        return recommendations;
    }

    private KoiRecommendationResponse buildPackageRecommendation(KoiConversationSession session, TravelPackageResponse pkg) {
        FlightResponse flight = null;
        HotelResponse hotel = null;

        try {
            if (pkg.getFlightNumber() != null && !pkg.getFlightNumber().isBlank()) {
                flight = flightCatalogClient.findByNumber(pkg.getFlightNumber());
            }
        } catch (KoiCatalogUnavailableException ignored) {
            // KOI prioriza la recomendacion base del paquete si el vuelo no se puede enriquecer.
        }

        try {
            if (pkg.getHotelId() != null) {
                hotel = hotelCatalogClient.findById(pkg.getHotelId());
            }
        } catch (KoiCatalogUnavailableException ignored) {
            // KOI prioriza la recomendacion base del paquete si el hotel no se puede enriquecer.
        }

        String title = safeText(pkg.getName(), "Paquete KOI");
        String summary = safeText(pkg.getDescription(), "Combina vuelo y hotel en una sola propuesta.");
        String whyItFits = friendlyPackageReason(session, pkg, flight, hotel);

        return KoiRecommendationResponse.builder()
                .type("PACKAGE")
                .title(title)
                .summary(summary)
                .price(pkg.getBasePrice())
                .destination(pkg.getDestination())
                .flightNumber(pkg.getFlightNumber())
                .hotelId(pkg.getHotelId())
                .hotelName(hotel != null ? hotel.getNombre() : null)
                .hotelCity(hotel != null ? hotel.getCiudad() : pkg.getDestination())
                .hotelStars(hotel != null ? hotel.getEstrellas() : null)
                .hotelPricePerNight(hotel != null ? hotel.getPrecioPorNoche() : null)
                .availableSeats(flight != null ? flight.getAvailableSeats() : null)
                .departureTime(flight != null ? flight.getDepartureTime() : null)
                .arrivalTime(flight != null ? flight.getArrivalTime() : null)
                .durationNights(pkg.getDurationNights())
                .whyItFits(whyItFits)
                .build();
    }

    private KoiRecommendationResponse buildFlightRecommendation(KoiConversationSession session, FlightResponse flight) {
        return KoiRecommendationResponse.builder()
                .type("FLIGHT")
                .title("Vuelo " + flight.getFlightNumber())
                .summary("Vuelo hacia " + destinationName(flight) + " con disponibilidad actual.")
                .price(flight.getPrice())
                .destination(destinationName(flight))
                .flightNumber(flight.getFlightNumber())
                .availableSeats(flight.getAvailableSeats())
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .whyItFits(friendlyFlightReason(session, flight))
                .build();
    }

    private KoiRecommendationResponse buildHotelRecommendation(KoiConversationSession session, HotelResponse hotel) {
        return KoiRecommendationResponse.builder()
                .type("HOTEL")
                .title(hotel.getNombre())
                .summary(hotel.getEstrellas() + " estrellas en " + hotel.getCiudad())
                .price(BigDecimal.valueOf(hotel.getPrecioPorNoche()))
                .destination(hotel.getCiudad())
                .hotelId(hotel.getId())
                .hotelName(hotel.getNombre())
                .hotelCity(hotel.getCiudad())
                .hotelStars(hotel.getEstrellas())
                .hotelPricePerNight(hotel.getPrecioPorNoche())
                .whyItFits(friendlyHotelReason(session, hotel))
                .build();
    }

    private String composeRecommendationReply(KoiConversationSession session, List<KoiRecommendationResponse> recommendations) {
        if (recommendations.isEmpty()) {
            return "No encontré algo exacto, pero te puedo mostrar opciones parecidas si flexibilizamos un poquito el presupuesto o las fechas.";
        }

        StringBuilder reply = new StringBuilder();
        reply.append("¡Te encontré opciones lindas dentro de tu presupuesto! 😄\n");
        reply.append("Las ordené priorizando lo que me contaste");
        if (!isBlank(session.getTravelStyle())) {
            reply.append(" y tu estilo de viaje ").append(session.getTravelStyle().toLowerCase(Locale.ROOT));
        }
        reply.append(".\n");
        reply.append("Si querés, después te ayudo a afinar cuál conviene más por comodidad, precio o ubicación.");
        return reply.toString();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "tu presupuesto";
        }
        return "$" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private KoiConversationResponse toConversationResponse(KoiConversationSession session,
                                                           String reply,
                                                           boolean needsMoreInfo,
                                                           MissingInfoField nextQuestion,
                                                           List<?> recommendations) {
        List<MissingInfoField> missing = determineMissingFields(session);
        List<KoiRecommendationResponse> typedRecommendations = recommendations.stream()
                .filter(KoiRecommendationResponse.class::isInstance)
                .map(KoiRecommendationResponse.class::cast)
                .toList();

        return KoiConversationResponse.builder()
                .sessionId(session.getId())
                .reply(reply)
                .needsMoreInfo(needsMoreInfo)
                .nextQuestion(nextQuestion)
                .missingFields(missing)
                .intent(session.getIntent())
                .stage(session.getStage())
                .recommendations(typedRecommendations)
                .build();
    }

    private KoiSessionResponse toSessionResponse(KoiConversationSession session) {
        return KoiSessionResponse.builder()
                .sessionId(session.getId())
                .stage(session.getStage())
                .intent(session.getIntent())
                .awaitingField(session.getAwaitingField())
                .userIdentifier(session.getUserIdentifier())
                .budget(session.getBudget())
                .travelers(session.getTravelers())
                .destination(session.getDestination())
                .origin(session.getOrigin())
                .travelStyle(session.getTravelStyle())
                .nights(session.getNights())
                .travelMonth(session.getTravelMonth())
                .departureDate(session.getDepartureDate())
                .returnDate(session.getReturnDate())
                .preferredBudget(session.getPreferredBudget())
                .preferredTravelers(session.getPreferredTravelers())
                .preferredDestination(session.getPreferredDestination())
                .preferredOrigin(session.getPreferredOrigin())
                .preferredTravelStyle(session.getPreferredTravelStyle())
                .preferredNights(session.getPreferredNights())
                .preferredSoloTravel(session.getPreferredSoloTravel())
                .preferredDepartureDate(session.getPreferredDepartureDate())
                .preferredReturnDate(session.getPreferredReturnDate())
                .userGoal(session.getUserGoal())
                .lastAssistantMessage(session.getLastAssistantMessage())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private void saveUserMessage(KoiConversationSession session, String message) {
        KoiConversationMessage conversationMessage = new KoiConversationMessage();
        conversationMessage.setSession(session);
        conversationMessage.setRole(MessageRole.USER);
        conversationMessage.setContent(message);
        messageRepository.save(conversationMessage);
    }

    private void saveAssistantMessage(KoiConversationSession session, String message) {
        KoiConversationMessage conversationMessage = new KoiConversationMessage();
        conversationMessage.setSession(session);
        conversationMessage.setRole(MessageRole.KOI);
        conversationMessage.setContent(message);
        messageRepository.save(conversationMessage);
    }

    private void syncPreferenceSnapshot(KoiConversationSession session) {
        if (session.getBudget() != null) {
            session.setPreferredBudget(session.getBudget());
        }
        if (session.getTravelers() != null) {
            session.setPreferredTravelers(session.getTravelers());
            session.setPreferredSoloTravel(session.getTravelers() == 1);
        }
        if (!isBlank(session.getDestination())) {
            session.setPreferredDestination(session.getDestination());
        }
        if (!isBlank(session.getOrigin())) {
            session.setPreferredOrigin(session.getOrigin());
        }
        if (!isBlank(session.getTravelStyle())) {
            session.setPreferredTravelStyle(session.getTravelStyle());
        }
        if (session.getNights() != null) {
            session.setPreferredNights(session.getNights());
        }
        if (session.getDepartureDate() != null) {
            session.setPreferredDepartureDate(session.getDepartureDate());
        }
        if (session.getReturnDate() != null) {
            session.setPreferredReturnDate(session.getReturnDate());
        }
    }

    private String buildGreeting(KoiConversationSession session) {
        StringBuilder reply = new StringBuilder("¡Hola! Soy KOI ✨\n");
        if (!isBlank(session.getPreferredDestination())
                || !isBlank(session.getPreferredOrigin())
                || session.getPreferredBudget() != null
                || !isBlank(session.getPreferredTravelStyle())) {
            reply.append("Retomé tus últimas preferencias para no arrancar de cero.\n");
            if (!isBlank(session.getPreferredDestination())) {
                reply.append("Destino favorito: ").append(session.getPreferredDestination()).append(". ");
            }
            if (session.getPreferredBudget() != null) {
                reply.append("Presupuesto habitual: ").append(formatMoney(session.getPreferredBudget())).append(". ");
            }
            if (!isBlank(session.getPreferredTravelStyle())) {
                reply.append("Te gusta viajar en modo ").append(session.getPreferredTravelStyle().toLowerCase(Locale.ROOT)).append(". ");
            }
            if (session.getPreferredSoloTravel() != null) {
                reply.append(Boolean.TRUE.equals(session.getPreferredSoloTravel()) ? "Sueles viajar solo/a. " : "Sueles viajar en grupo. ");
            }
            reply.append("\n¿Querés seguir con esa idea o probamos algo distinto?");
            return reply.toString().trim();
        }

        reply.append("¿En qué puedo ayudarte hoy?\n");
        reply.append("Contame qué tipo de viaje querés armar y te voy guiando paso a paso.");
        return reply.toString();
    }

    private String normalizeUserIdentifier(String userIdentifier) {
        if (isBlank(userIdentifier)) {
            return null;
        }
        return userIdentifier.trim().toLowerCase(Locale.ROOT);
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String effectiveDestination(KoiConversationSession session) {
        if (!isBlank(session.getDestination())) {
            return session.getDestination();
        }
        return session.getPreferredDestination();
    }

    private BigDecimal effectiveBudget(KoiConversationSession session) {
        if (session.getBudget() != null) {
            return session.getBudget();
        }
        return session.getPreferredBudget();
    }

    private boolean matchesMonth(Integer monthValue, String monthText) {
        if (monthValue == null || isBlank(monthText)) {
            return false;
        }
        String normalized = normalize(monthText);
        List<String> names = spanishMonths();
        for (int i = 0; i < names.size(); i++) {
            if (normalized.contains(names.get(i))) {
                return monthValue == i + 1;
            }
        }
        return false;
    }

    private boolean matchesHotelStyle(String travelStyle, HotelResponse hotel) {
        if (isBlank(travelStyle) || hotel == null) {
            return true;
        }
        String normalized = normalize(travelStyle);
        if (normalized.contains("playa") || normalized.contains("relax")) {
            return Boolean.TRUE.equals(hotel.getAllInclusive()) || hotel.getEstrellas() >= 4;
        }
        if (normalized.contains("aventura") || normalized.contains("ciudad") || normalized.contains("cultural")) {
            return true;
        }
        return true;
    }

    private String friendlyPackageReason(KoiConversationSession session, TravelPackageResponse pkg, FlightResponse flight, HotelResponse hotel) {
        List<String> reasons = new ArrayList<>();
        if (pkg.getBasePrice() != null && effectiveBudget(session) != null) {
            reasons.add("entra en tu presupuesto");
        }
        if (session.getNights() != null && pkg.getDurationNights() != null) {
            reasons.add("se acerca bastante a la cantidad de noches que querés");
        }
        if (hotel != null) {
            reasons.add("incluye hotel");
        }
        if (flight != null) {
            reasons.add("trae el vuelo asociado");
        }
        if (reasons.isEmpty()) {
            return "Es una propuesta equilibrada y cómoda para arrancar.";
        }
        return "Te conviene porque " + String.join(", ", reasons) + ".";
    }

    private String friendlyFlightReason(KoiConversationSession session, FlightResponse flight) {
        List<String> reasons = new ArrayList<>();
        if (flight.getPrice() != null && effectiveBudget(session) != null) {
            reasons.add("queda bien con tu presupuesto");
        }
        if (flight.getAvailableSeats() != null) {
            reasons.add("todavía tiene asientos disponibles");
        }
        if (session.getOrigin() != null && flight.getOriginAirport() != null) {
            reasons.add("sale desde " + flight.getOriginAirport().getCity());
        }
        if (reasons.isEmpty()) {
            return "Es una alternativa compatible con tu viaje.";
        }
        return "Te conviene porque " + String.join(" y ", reasons) + ".";
    }

    private String friendlyHotelReason(KoiConversationSession session, HotelResponse hotel) {
        List<String> reasons = new ArrayList<>();
        if (hotel.getPrecioPorNoche() > 0 && effectiveBudget(session) != null) {
            reasons.add("tiene buen equilibrio entre precio y comodidad");
        }
        if (hotel.getHabitacionesDisponibles() > 0) {
            reasons.add("todavía tiene disponibilidad");
        }
        if (!isBlank(session.getTravelStyle())) {
            reasons.add("acompaña tu estilo de viaje " + session.getTravelStyle().toLowerCase(Locale.ROOT));
        }
        if (reasons.isEmpty()) {
            return "Es una buena base de alojamiento para tu viaje.";
        }
        return "Te conviene porque " + String.join(" y ", reasons) + ".";
    }

    private BigDecimal extractBudget(String message) {
        String normalized = normalize(message);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{1,3}(?:[\\.,]\\d{3})+|\\d+)")
                .matcher(normalized);
        BigDecimal biggest = null;
        while (matcher.find()) {
            String rawNumber = matcher.group(1).replace(".", "").replace(",", "");
            try {
                BigDecimal candidate = new BigDecimal(rawNumber);
                if (!containsAny(normalized, "presupuesto", "budget", "$", "ars", "pesos", "cuento con", "tengo")) {
                    if (rawNumber.length() == 4) {
                        int numericYear = Integer.parseInt(rawNumber);
                        if (numericYear >= 1900 && numericYear <= 2100) {
                            continue;
                        }
                    }
                }
                if (biggest == null || candidate.compareTo(biggest) > 0) {
                    biggest = candidate;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (biggest == null) {
            return null;
        }

        if (containsAny(normalized, "presupuesto", "budget", "$", "ars", "pesos", "cuento con", "tengo")) {
            return biggest;
        }

        return biggest.compareTo(BigDecimal.valueOf(1000)) >= 0 ? biggest : null;
    }

    private Integer extractTravelers(String message) {
        String normalized = normalize(message);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(?:somos|viajamos|viajan|viajo|viaja|viajaremos)\\s*(\\d+)")
                .matcher(normalized);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        matcher = java.util.regex.Pattern
                .compile("(?i)(\\d+)\\s*(?:personas?|pasajeros?|viajeros?)")
                .matcher(normalized);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        if (containsAny(normalized, "viajo solo", "voy solo", "viajo sola", "voy sola", "solo yo")) {
            return 1;
        }

        return null;
    }

    private String extractMonth(String message) {
        String normalized = normalize(message);
        for (String month : spanishMonths()) {
            if (normalized.contains(month)) {
                return capitalize(month);
            }
        }
        return null;
    }

    private String extractOrigin(String message) {
        String normalized = normalize(message);
        String[] cues = {
                "desde ", "saliendo de ", "partiendo de ", "origen ", "salida desde "
        };
        for (String cue : cues) {
            int index = normalized.indexOf(cue);
            if (index >= 0) {
                String candidate = normalized.substring(index + cue.length()).trim();
                candidate = candidate.replaceAll("[\\.,;:!?].*$", "").trim();
                candidate = candidate.replaceAll("\\b(?:para|con|en|hacia|al|la|el)\\b.*$", "").trim();
                if (!candidate.isBlank() && !candidate.matches("^[0-9].*")) {
                    return capitalizeWords(firstWords(candidate, 4));
                }
            }
        }
        return null;
    }

    private String extractTravelStyle(String message) {
        String normalized = normalize(message);
        if (containsAny(normalized, "playa", "mar", "costa", "costa")) {
            return "PLAYA";
        }
        if (containsAny(normalized, "ciudad", "urbano", "urbana")) {
            return "CIUDAD";
        }
        if (containsAny(normalized, "relax", "descanso", "spa", "tranquilo")) {
            return "RELAX";
        }
        if (containsAny(normalized, "aventura", "trekking", "senderismo", "montaña", "montana")) {
            return "AVENTURA";
        }
        if (containsAny(normalized, "cultural", "museos", "historia", "histórico", "historico")) {
            return "CULTURAL";
        }
        return null;
    }

    private Integer extractNights(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(\\d+)\\s*(?:noches?|nights?|dias?|días?)")
                .matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private LocalDate extractDepartureDate(String message) {
        String normalized = normalize(message);
        java.util.regex.Matcher numericRange = java.util.regex.Pattern
                .compile("(?i)(?:del|desde|salida el|salgo el|viajo el)\\s+(\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?)")
                .matcher(normalized);
        if (numericRange.find()) {
            return parseFlexibleDate(numericRange.group(1));
        }

        java.util.regex.Matcher longForm = java.util.regex.Pattern
                .compile("(?i)(?:el|para el|salida el|viajo el)\\s+(\\d{1,2})\\s+de\\s+([a-záéíóúñ]+)(?:\\s+de\\s+(\\d{4}))?")
                .matcher(normalized);
        if (longForm.find()) {
            return parseLongFormDate(longForm.group(1), longForm.group(2), longForm.group(3));
        }

        return null;
    }

    private LocalDate extractReturnDate(String message) {
        String normalized = normalize(message);
        java.util.regex.Matcher range = java.util.regex.Pattern
                .compile("(?i)(?:del|desde)\\s+(\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?)\\s+(?:al|hasta)\\s+(\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?)")
                .matcher(normalized);
        if (range.find()) {
            return parseFlexibleDate(range.group(2));
        }

        java.util.regex.Matcher longFormRange = java.util.regex.Pattern
                .compile("(?i)(?:del|desde)\\s+(\\d{1,2})\\s+de\\s+([a-záéíóúñ]+)(?:\\s+de\\s+(\\d{4}))?\\s+(?:al|hasta)\\s+(\\d{1,2})\\s+de\\s+([a-záéíóúñ]+)(?:\\s+de\\s+(\\d{4}))?")
                .matcher(normalized);
        if (longFormRange.find()) {
            String year = longFormRange.group(3) != null ? longFormRange.group(3) : longFormRange.group(6);
            return parseLongFormDate(longFormRange.group(4), longFormRange.group(5), year);
        }

        return null;
    }

    private LocalDate parseFlexibleDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        String normalized = rawDate.replace('.', '/').replace('-', '/');
        String[] parts = normalized.split("/");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }

        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year = parts.length == 3 ? Integer.parseInt(normalizeYear(parts[2])) : defaultYearForMonth(month);

        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDate parseLongFormDate(String dayText, String monthText, String yearText) {
        Integer month = monthNumber(monthText);
        if (month == null) {
            return null;
        }
        int day = Integer.parseInt(dayText);
        int year = yearText != null ? Integer.parseInt(yearText) : defaultYearForMonth(month);
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String normalizeYear(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() == 2) {
            return "20" + normalized;
        }
        return normalized;
    }

    private Integer monthNumber(String monthText) {
        if (isBlank(monthText)) {
            return null;
        }
        String normalized = normalize(monthText);
        List<String> months = spanishMonths();
        for (int i = 0; i < months.size(); i++) {
            if (normalized.contains(months.get(i))) {
                return i + 1;
            }
        }
        return null;
    }

    private int defaultYearForMonth(int month) {
        int currentYear = Year.now().getValue();
        int currentMonth = LocalDate.now().getMonthValue();
        return month < currentMonth ? currentYear + 1 : currentYear;
    }

    private String extractDestination(String message) {
        String normalized = normalize(message);
        String[] cues = {
                "viajar a ", "ir a ", "vacaciones en ", "destino ", "a ", "hacia ", "en "
        };
        for (String cue : cues) {
            int index = normalized.indexOf(cue);
            if (index >= 0) {
                String candidate = normalized.substring(index + cue.length()).trim();
                candidate = candidate.replaceAll("[\\.,;:!?].*$", "").trim();
                candidate = candidate.replaceAll("\\b(?:desde|con|para|porque|por|este|esta|en|durante|conoce|conocer|hacia|al|la|el|y)\\b.*$", "").trim();
                if (!candidate.isBlank() && !candidate.matches("^[0-9].*")) {
                    return capitalizeWords(firstWords(candidate, 4));
                }
            }
        }
        return null;
    }

    private String firstWords(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) {
            return text.trim();
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) {
                result.append(' ');
            }
            result.append(words[i]);
        }
        return result.toString().trim();
    }

    private boolean matchesDestination(String source, String destination) {
        if (isBlank(source) || isBlank(destination)) {
            return false;
        }
        return normalize(source).contains(normalize(destination));
    }

    private BigDecimal perPersonBudget(KoiConversationSession session, BigDecimal flightPrice) {
        if (session.getTravelers() == null || session.getTravelers() <= 0) {
            return flightPrice;
        }
        return session.getBudget().divide(BigDecimal.valueOf(session.getTravelers()), 2, RoundingMode.HALF_UP);
    }

    private String destinationName(FlightResponse flight) {
        if (flight.getDestinationAirport() == null) {
            return "destino seleccionado";
        }
        if (!isBlank(flight.getDestinationAirport().getCity())) {
            return flight.getDestinationAirport().getCity();
        }
        return safeText(flight.getDestinationAirport().getCountry(), "destino seleccionado");
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private String safeText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String capitalize(String value) {
        if (isBlank(value)) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String capitalizeWords(String value) {
        if (isBlank(value)) {
            return value;
        }
        String[] words = value.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(capitalize(word));
        }
        return builder.toString();
    }

    private List<String> spanishMonths() {
        return List.of(
                "enero", "febrero", "marzo", "abril", "mayo", "junio",
                "julio", "agosto", "septiembre", "setiembre", "octubre",
                "noviembre", "diciembre"
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
