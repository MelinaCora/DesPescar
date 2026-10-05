package com.despescar.koiiaservice.service;

import com.despescar.koiiaservice.dto.request.KoiConversationMessageRequest;
import com.despescar.koiiaservice.dto.response.KoiConversationResponse;
import com.despescar.koiiaservice.dto.response.KoiSessionResponse;
import com.despescar.koiiaservice.entity.KoiConversationMessage;
import com.despescar.koiiaservice.entity.KoiConversationSession;
import com.despescar.koiiaservice.enums.ConversationStage;
import com.despescar.koiiaservice.enums.MessageRole;
import com.despescar.koiiaservice.enums.UserIntent;
import com.despescar.koiiaservice.exception.KoiSessionNotFoundException;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoiConversationServiceTest {

    @Mock
    private KoiConversationSessionRepository sessionRepository;

    @Mock
    private KoiConversationMessageRepository messageRepository;

    @Mock
    private KoiAiAssistant koiAiAssistant;

    @InjectMocks
    private KoiConversationService koiConversationService;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
    }

    private KoiConversationSession existingSession(String userIdentifier) {
        KoiConversationSession session = new KoiConversationSession();
        session.setId(sessionId);
        session.setUserIdentifier(userIdentifier);
        session.setStage(ConversationStage.COLLECTING_INFO);
        session.setIntent(UserIntent.UNKNOWN);
        return session;
    }

    private KoiConversationMessageRequest request(String message) {
        KoiConversationMessageRequest request = new KoiConversationMessageRequest();
        request.setMessage(message);
        return request;
    }

    @Test
    void startSessionCreaLaSesionNormalizaElUsuarioYGuardaElSaludo() {
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> {
            KoiConversationSession session = invocation.getArgument(0);
            session.setId(sessionId);
            return session;
        });

        KoiConversationResponse response = koiConversationService.startSession("  Test@Example.COM ");

        assertEquals(sessionId, response.getSessionId());
        assertTrue(response.getReply().startsWith("¡Hola! Soy KOI"));
        assertFalse(response.isNeedsMoreInfo());
        assertEquals(ConversationStage.COLLECTING_INFO, response.getStage());
        assertEquals(UserIntent.UNKNOWN, response.getIntent());
        assertTrue(response.getRecommendations().isEmpty());

        ArgumentCaptor<KoiConversationSession> savedSession = ArgumentCaptor.forClass(KoiConversationSession.class);
        verify(sessionRepository).save(savedSession.capture());
        assertEquals("test@example.com", savedSession.getValue().getUserIdentifier());

        ArgumentCaptor<KoiConversationMessage> savedMessage = ArgumentCaptor.forClass(KoiConversationMessage.class);
        verify(messageRepository).save(savedMessage.capture());
        assertEquals(MessageRole.KOI, savedMessage.getValue().getRole());
        assertEquals(response.getReply(), savedMessage.getValue().getContent());
    }

    @Test
    void startSessionSinUsuarioCreaUnaSesionAnonima() {
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        koiConversationService.startSession(null);

        ArgumentCaptor<KoiConversationSession> savedSession = ArgumentCaptor.forClass(KoiConversationSession.class);
        verify(sessionRepository).save(savedSession.capture());
        assertNull(savedSession.getValue().getUserIdentifier());
    }

    @Test
    void handleMessageDelegaEnElAsistenteConElMensajeYElHistorial() {
        KoiConversationSession session = existingSession("test@example.com");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KoiConversationMessageRequest request = request("  Quiero viajar a Bariloche  ");
        KoiConversationMessageRequest.MensajeHistorialDto previo = new KoiConversationMessageRequest.MensajeHistorialDto();
        previo.setRole("user");
        previo.setContent("Hola");
        request.setHistory(List.of(previo));
        when(koiAiAssistant.procesarConversacion("Quiero viajar a Bariloche", request.getHistory()))
                .thenReturn("¡Bariloche es un gran destino!");

        KoiConversationResponse response = koiConversationService.handleMessage(sessionId, request, "test@example.com");

        assertEquals("¡Bariloche es un gran destino!", response.getReply());
        assertEquals(sessionId, response.getSessionId());
        assertFalse(response.isNeedsMoreInfo());
        assertTrue(response.getRecommendations().isEmpty());
        assertEquals("¡Bariloche es un gran destino!", session.getLastAssistantMessage());
    }

    @Test
    void handleMessageGuardaElMensajeDelUsuarioYLaRespuestaDeKoi() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession(null)));
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(koiAiAssistant.procesarConversacion(anyString(), any())).thenReturn("Respuesta de KOI");

        koiConversationService.handleMessage(sessionId, request("Hola KOI"), null);

        ArgumentCaptor<KoiConversationMessage> saved = ArgumentCaptor.forClass(KoiConversationMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(MessageRole.USER, saved.getAllValues().get(0).getRole());
        assertEquals("Hola KOI", saved.getAllValues().get(0).getContent());
        assertEquals(MessageRole.KOI, saved.getAllValues().get(1).getRole());
        assertEquals("Respuesta de KOI", saved.getAllValues().get(1).getContent());
    }

    @Test
    void handleMessageRechazaUnaSesionDeOtroUsuarioSinLlamarALaIa() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession("duenio@example.com")));

        assertThrows(IllegalArgumentException.class,
                () -> koiConversationService.handleMessage(sessionId, request("Hola"), "intruso@example.com"));

        verify(koiAiAssistant, never()).procesarConversacion(anyString(), anyList());
        verify(messageRepository, never()).save(any(KoiConversationMessage.class));
    }

    @Test
    void handleMessagePermiteAlDuenioAunqueElCorreoVengaConOtraMayuscula() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession("duenio@example.com")));
        when(sessionRepository.save(any(KoiConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(koiAiAssistant.procesarConversacion(anyString(), any())).thenReturn("ok");

        KoiConversationResponse response = koiConversationService.handleMessage(sessionId, request("Hola"), "Duenio@Example.com");

        assertEquals("ok", response.getReply());
    }

    @Test
    void handleMessageConSesionInexistenteLanzaNotFound() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThrows(KoiSessionNotFoundException.class,
                () -> koiConversationService.handleMessage(sessionId, request("Hola"), null));

        verify(koiAiAssistant, never()).procesarConversacion(anyString(), any());
    }

    @Test
    void getSessionDevuelveElEstadoDeLaConversacion() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession("test@example.com")));

        KoiSessionResponse response = koiConversationService.getSession(sessionId);

        assertEquals(sessionId, response.getSessionId());
        assertEquals("test@example.com", response.getUserIdentifier());
        assertEquals(ConversationStage.COLLECTING_INFO, response.getStage());
        assertEquals(UserIntent.UNKNOWN, response.getIntent());
    }

    @Test
    void getSessionInexistenteLanzaNotFound() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThrows(KoiSessionNotFoundException.class, () -> koiConversationService.getSession(sessionId));
    }
}
