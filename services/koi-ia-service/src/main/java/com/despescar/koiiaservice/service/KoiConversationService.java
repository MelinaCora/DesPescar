package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.dto.request.KoiConversationMessageRequest;
import com.despescar.koiiaservice.dto.response.KoiConversationResponse;
import com.despescar.koiiaservice.dto.response.KoiSessionResponse;
import com.despescar.koiiaservice.entity.KoiConversationMessage;
import com.despescar.koiiaservice.entity.KoiConversationSession;
import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MessageRole;
import com.despescar.koiiaservice.enums.MissingInfoField;
import com.despescar.koiiaservice.enums.UserIntent;
import com.despescar.koiiaservice.exception.KoiSessionNotFoundException;
import com.despescar.koiiaservice.repository.KoiConversationMessageRepository;
import com.despescar.koiiaservice.repository.KoiConversationSessionRepository;
import com.despescar.koiiaservice.service.ai.KoiAiAssistant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KoiConversationService {

    private final KoiConversationSessionRepository sessionRepository;
    private final KoiConversationMessageRepository messageRepository;
    private final KoiAiAssistant koiAiAssistant;

    @Transactional
    public KoiConversationResponse startSession(String userIdentifier) {
        KoiConversationSession session = new KoiConversationSession();
        session.setUserIdentifier(normalizeUserIdentifier(userIdentifier));
        session.setStage(ConversationStage.COLLECTING_INFO);
        session.setIntent(UserIntent.VACATION);
        session = sessionRepository.save(session);

        String reply = "¡Hola! Soy KOI ✨ Tu asistente de viajes en DesPescar. ¿A dónde te gustaría viajar y con qué presupuesto cuentas?";
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

        String message = request.getMessage().trim();
        saveUserMessage(session, message);

        // Delegamos la inteligencia pasando el mensaje actual y el historial que viene de React
        String respuestaIa = koiAiAssistant.procesarConversacion(message, request.getHistory());

        session.setLastAssistantMessage(respuestaIa);
        session = sessionRepository.save(session);
        saveAssistantMessage(session, respuestaIa);

        return toConversationResponse(session, respuestaIa, false, null, List.of());
    }

    private KoiConversationSession loadSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new KoiSessionNotFoundException(sessionId));
    }

    private void ensureSessionOwner(KoiConversationSession session, String userIdentifier) {
        String normalizedUser = normalizeUserIdentifier(userIdentifier);
        if (normalizedUser != null && session.getUserIdentifier() != null && !session.getUserIdentifier().equals(normalizedUser)) {
            throw new IllegalArgumentException("La sesión KOI no pertenece al usuario autenticado.");
        }
    }

    private String normalizeUserIdentifier(String userIdentifier) {
        return userIdentifier == null ? null : userIdentifier.trim().toLowerCase(Locale.ROOT);
    }

    private void saveUserMessage(KoiConversationSession session, String message) {
        KoiConversationMessage m = new KoiConversationMessage();
        m.setSession(session);
        m.setRole(MessageRole.USER);
        m.setContent(message);
        messageRepository.save(m);
    }

    private void saveAssistantMessage(KoiConversationSession session, String message) {
        KoiConversationMessage m = new KoiConversationMessage();
        m.setSession(session);
        m.setRole(MessageRole.KOI);
        m.setContent(message);
        messageRepository.save(m);
    }

    private KoiConversationResponse toConversationResponse(KoiConversationSession session, String reply, boolean needsMoreInfo, MissingInfoField nextQuestion, List<?> recs) {
        return KoiConversationResponse.builder()
                .sessionId(session.getId())
                .reply(reply)
                .needsMoreInfo(needsMoreInfo)
                .nextQuestion(nextQuestion)
                .missingFields(List.of())
                .intent(session.getIntent())
                .stage(session.getStage())
                .recommendations(List.of())
                .build();
    }

    private KoiSessionResponse toSessionResponse(KoiConversationSession session) {
        return KoiSessionResponse.builder()
                .sessionId(session.getId())
                .stage(session.getStage())
                .intent(session.getIntent())
                .userIdentifier(session.getUserIdentifier())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}