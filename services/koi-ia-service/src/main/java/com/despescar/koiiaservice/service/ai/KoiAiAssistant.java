package com.despescar.koiiaservice.service.ai;

import com.despescar.koiiaservice.client.OllamaClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Capa de IA (LLM) que COMPLEMENTA la lógica de reglas de KOI, no la reemplaza.
 * <p>
 * - Si {@code koi.ai.enabled=false} (default), todos los métodos devuelven Optional.empty()
 *   y KOI sigue funcionando 100% con su motor de reglas (regex + memoria de sesión).
 * - Si está habilitado, se usa un modelo local via Ollama para:
 *   1) mejorar la extracción de datos de mensajes libres (extractTravelInfo)
 *   2) redactar respuestas más humanas y variadas (humanize*)
 * <p>
 * El LLM nunca decide qué paquetes/vuelos/hoteles recomendar ni inventa precios:
 * esos datos siempre provienen de los microservicios de catálogo. El LLM solo
 * interpreta texto libre y redacta, evitando alucinaciones sobre datos de negocio.
 */
@Slf4j
@Component
public class KoiAiAssistant {

    private final OllamaClient ollamaClient;
    private final boolean enabled;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public KoiAiAssistant(OllamaClient ollamaClient,
                           @Value("${koi.ai.enabled:false}") boolean enabled) {
        this.ollamaClient = ollamaClient;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Pide al LLM que extraiga datos de viaje de un mensaje en lenguaje libre.
     * Se usa como complemento del extractor por regex: solo se aplican los campos
     * que el motor de reglas no haya podido detectar.
     */
    public Optional<ExtractedTravelInfo> extractTravelInfo(String userMessage) {
        if (!enabled) {
            return Optional.empty();
        }
        String prompt = buildExtractionPrompt(userMessage);
        return ollamaClient.generate(prompt, true).flatMap(this::parseExtraction);
    }

    public Optional<String> humanizeGreeting(String baseMessage) {
        return humanizeText(
                "Reescribí este saludo de bienvenida de KOI, el chatbot de viajes, para que suene cálido, "
                        + "entusiasta y natural. Mantené toda la información mencionada, no agregues datos nuevos.",
                baseMessage);
    }

    public Optional<String> humanizeQuestion(String baseQuestion) {
        return humanizeText(
                "Reformulá esta pregunta de KOI, el chatbot de viajes, para que suene natural, cercana y amigable, "
                        + "sin perder su sentido ni agregar preguntas nuevas.",
                baseQuestion);
    }

    public Optional<String> humanizeRecommendationReply(String baseSummary, String recommendationsContext) {
        return humanizeText(
                "Sos KOI, un asistente de viajes con personalidad cálida y entusiasta. Redactá una respuesta breve "
                        + "presentando estas recomendaciones de forma humana y natural. No inventes datos, precios "
                        + "ni destinos nuevos: basate únicamente en la información provista.",
                baseSummary + "\nOpciones encontradas:\n" + recommendationsContext);
    }

    public Optional<String> humanizeFallback(String baseMessage) {
        return humanizeText(
                "Reescribí este mensaje de KOI (chatbot de viajes) para cuando no se encontraron resultados exactos. "
                        + "Debe sonar empático, positivo y proponer alternativas, sin sonar negativo ni robótico.",
                baseMessage);
    }

    private Optional<String> humanizeText(String instruction, String context) {
        if (!enabled || context == null || context.isBlank()) {
            return Optional.empty();
        }
        String prompt = instruction
                + "\n\nTexto base:\n\"" + context + "\""
                + "\n\nRespondé ÚNICAMENTE con el texto final en español, sin comillas, sin explicaciones "
                + "adicionales y en un máximo de 3 oraciones.";
        return ollamaClient.generate(prompt, false)
                .map(String::trim)
                .filter(text -> !text.isBlank());
    }

    private String buildExtractionPrompt(String message) {
        return """
                Sos un extractor de datos de viajes. Del siguiente mensaje de un usuario, extraé SOLO los datos \
                que estén explícitamente mencionados o puedan inferirse con alta confianza.
                Devolvé EXCLUSIVAMENTE un JSON válido (sin texto adicional, sin markdown) con esta forma exacta, \
                usando null en los campos no mencionados:
                {"budget": number|null, "travelers": number|null, "destination": string|null, "origin": string|null, \
                "travelStyle": "playa"|"ciudad"|"relax"|"aventura"|null, "nights": number|null, "month": string|null}

                Mensaje del usuario: "%s"
                """.formatted(message);
    }

    private Optional<ExtractedTravelInfo> parseExtraction(String rawResponse) {
        String json = extractJsonObject(rawResponse);
        if (json == null) {
            log.warn("La respuesta de Ollama no contenía un JSON válido para extracción: {}", rawResponse);
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ExtractedTravelInfo.class));
        } catch (Exception ex) {
            log.warn("No se pudo parsear el JSON de extracción de Ollama: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            return null;
        }
        return text.substring(start, end + 1);
    }
}
