package com.despescar.koiiaservice.service.ai;

import com.despescar.koiiaservice.client.OllamaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoiAiAssistantTest {

    @Mock
    private OllamaClient ollamaClient;

    @Test
    void whenAiDisabledExtractionReturnsEmpty() {
        KoiAiAssistant assistant = new KoiAiAssistant(ollamaClient, false);

        Optional<ExtractedTravelInfo> result = assistant.extractTravelInfo("quiero viajar a Bariloche con 2000 dolares");

        assertTrue(result.isEmpty());
    }

    @Test
    void whenAiEnabledParsesValidJsonFromOllama() {
        KoiAiAssistant assistant = new KoiAiAssistant(ollamaClient, true);
        String rawJson = "{\"budget\": 2000, \"travelers\": 2, \"destination\": \"Bariloche\", "
                + "\"origin\": \"Buenos Aires\", \"travelStyle\": \"aventura\", \"nights\": 5, \"month\": \"julio\"}";
        when(ollamaClient.generate(anyString(), anyBoolean())).thenReturn(Optional.of(rawJson));

        Optional<ExtractedTravelInfo> result = assistant.extractTravelInfo("quiero ir a Bariloche, somos 2, salimos de Buenos Aires");

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("2000"), result.get().budget());
        assertEquals(2, result.get().travelers());
        assertEquals("Bariloche", result.get().destination());
        assertEquals("Buenos Aires", result.get().origin());
        assertEquals("aventura", result.get().travelStyle());
        assertEquals(5, result.get().nights());
        assertEquals("julio", result.get().month());
    }

    @Test
    void whenOllamaReturnsInvalidJsonExtractionFallsBackToEmpty() {
        KoiAiAssistant assistant = new KoiAiAssistant(ollamaClient, true);
        when(ollamaClient.generate(anyString(), anyBoolean())).thenReturn(Optional.of("esto no es un json"));

        Optional<ExtractedTravelInfo> result = assistant.extractTravelInfo("mensaje cualquiera");

        assertTrue(result.isEmpty());
    }

    @Test
    void whenOllamaUnavailableExtractionFallsBackGracefully() {
        KoiAiAssistant assistant = new KoiAiAssistant(ollamaClient, true);
        when(ollamaClient.generate(anyString(), anyBoolean())).thenReturn(Optional.empty());

        Optional<ExtractedTravelInfo> result = assistant.extractTravelInfo("mensaje cualquiera");

        assertTrue(result.isEmpty());
    }

    @Test
    void humanizeTextReturnsEmptyWhenDisabled() {
        KoiAiAssistant assistant = new KoiAiAssistant(ollamaClient, false);

        Optional<String> result = assistant.humanizeGreeting("¡Hola! ¿En qué puedo ayudarte?");

        assertTrue(result.isEmpty());
    }

    @Test
    void humanizeTextReturnsOllamaResponseWhenEnabled() {
        KoiAiAssistant assistant = new KoiAiAssistant(ollamaClient, true);
        when(ollamaClient.generate(anyString(), anyBoolean()))
                .thenReturn(Optional.of("¡Hola! Qué alegría tenerte por acá, ¿en qué te ayudo hoy?"));

        Optional<String> result = assistant.humanizeGreeting("¡Hola! ¿En qué puedo ayudarte?");

        assertTrue(result.isPresent());
        assertEquals("¡Hola! Qué alegría tenerte por acá, ¿en qué te ayudo hoy?", result.get());
    }
}
