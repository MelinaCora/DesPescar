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
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    public KoiConversationResponse startSession() {
        KoiConversationSession session = new KoiConversationSession();
        session.setStage(ConversationStage.COLLECTING_INFO);
        session.setIntent(UserIntent.UNKNOWN);
        session = sessionRepository.save(session);

        String reply = "¡Hola! Soy KOI ✨\n"
                + "Te ayudo a encontrar vacaciones, vuelos u hoteles que encajen con tu presupuesto.\n"
                + "Para arrancar, ¿con qué presupuesto querés que trabaje?";
        saveAssistantMessage(session, reply);
        return toConversationResponse(session, reply, true, MissingInfoField.BUDGET, List.of());
    }

    @Transactional(readOnly = true)
    public KoiSessionResponse getSession(UUID sessionId) {
        return toSessionResponse(loadSession(sessionId));
    }

    @Transactional
    public KoiConversationResponse handleMessage(UUID sessionId, KoiConversationMessageRequest request) {
        KoiConversationSession session = loadSession(sessionId);
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
        }

        Integer travelers = extractTravelers(message);
        if (travelers != null) {
            session.setTravelers(travelers);
        }

        String destination = extractDestination(message);
        if (destination != null) {
            session.setDestination(destination);
        }

        String month = extractMonth(message);
        if (month != null) {
            session.setTravelMonth(month);
        }

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
        if (isBlank(session.getTravelMonth())) {
            missing.add(MissingInfoField.MONTH);
        }

        return missing;
    }

    private String friendlyQuestion(MissingInfoField field, KoiConversationSession session) {
        return switch (field) {
            case BUDGET -> "¡Genial! Para arrancar bien, ¿con qué presupuesto querés que trabaje?";
            case TRAVELERS -> "¿Cuántas personas viajan?";
            case DESTINATION -> "¿Tenés algún destino en mente?";
            case MONTH -> "¿En qué mes te gustaría viajar?";
        };
    }

    private List<KoiRecommendationResponse> buildRecommendations(KoiConversationSession session) {
        List<KoiRecommendationResponse> recommendations = new ArrayList<>();

        List<TravelPackageResponse> packages = packageCatalogClient.searchPackages(session.getDestination(), session.getBudget());
        packages.stream()
                .filter(TravelPackageResponse::isActive)
                .sorted(Comparator.comparing(TravelPackageResponse::getBasePrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(3)
                .forEach(pkg -> recommendations.add(buildPackageRecommendation(session, pkg)));

        if (!recommendations.isEmpty()) {
            return recommendations;
        }

        List<FlightResponse> flights = flightCatalogClient.findAllFlights();
        flights.stream()
                .filter(flight -> flight.getDestinationAirport() != null)
                .filter(flight -> matchesDestination(flight.getDestinationAirport().getCity(), session.getDestination())
                        || matchesDestination(flight.getDestinationAirport().getCountry(), session.getDestination())
                        || matchesDestination(flight.getDestinationAirport().getName(), session.getDestination()))
                .filter(flight -> flight.getAvailableSeats() == null || flight.getAvailableSeats() > 0)
                .filter(flight -> session.getBudget() == null || flight.getPrice() == null
                        || flight.getPrice().compareTo(perPersonBudget(session, flight.getPrice())) <= 0)
                .sorted(Comparator.comparing(FlightResponse::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(2)
                .forEach(flight -> recommendations.add(buildFlightRecommendation(session, flight)));

        List<HotelResponse> hotels = hotelCatalogClient.findByCity(session.getDestination());
        hotels.stream()
                .filter(hotel -> hotel.getHabitacionesDisponibles() > 0)
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
        String whyItFits = "Encaja con tu presupuesto y ya viene armado para simplificar la compra.";

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
                .whyItFits("Es una alternativa compatible con tu destino y presupuesto.")
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
                .whyItFits("Es una buena base de alojamiento para tu viaje.")
                .build();
    }

    private String composeRecommendationReply(KoiConversationSession session, List<KoiRecommendationResponse> recommendations) {
        if (recommendations.isEmpty()) {
            return "Todavía no encontré una opción que cierre perfecto con esos datos. "
                    + "Si querés, ajustamos presupuesto, destino o mes y lo sigo afinando.";
        }

        StringBuilder reply = new StringBuilder();
        reply.append("¡Listo! Ya tengo opciones que encajan bastante bien con tu búsqueda 😄\n");
        reply.append("Ordené las mejores alternativas para ").append(session.getTravelers()).append(" persona(s) ")
                .append("con un presupuesto de ").append(formatMoney(session.getBudget())).append(".\n");
        reply.append("Te dejo las recomendaciones más prometedoras:");
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
                .budget(session.getBudget())
                .travelers(session.getTravelers())
                .destination(session.getDestination())
                .travelMonth(session.getTravelMonth())
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

    private BigDecimal extractBudget(String message) {
        String normalized = message.replace(".", "").replace(",", ".");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(?:\\$|ars\\s*)?(\\d+(?:\\.\\d+)?)")
                .matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer extractTravelers(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(\\d+)\\s*(?:personas?|pasajeros?|viajan|somos|viajamos)")
                .matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
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
                candidate = candidate.replaceAll("\\b(?:con|para|porque|por|este|esta|en|durante|conoce|conocer)\\b.*$", "").trim();
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
