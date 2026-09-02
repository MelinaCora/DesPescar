package com.despescar.koiiaservice.client;

import com.despescar.koiiaservice.client.dto.OllamaGenerateRequest;
import com.despescar.koiiaservice.client.dto.OllamaGenerateResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Cliente HTTP para Ollama (LLM local, sin costo por uso).
 * Si Ollama no está disponible o falla, devuelve Optional.empty() para que
 * KOI pueda seguir funcionando con su lógica de reglas como respaldo.
 */
@Slf4j
@Component
public class OllamaClient {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OllamaClient(@Value("${koi.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
                         @Value("${koi.ai.ollama.model:llama3.2}") String model,
                         @Value("${koi.ai.ollama.timeout-ms:8000}") long timeoutMs) {
        this.model = model;
        var stringConverter = new org.springframework.http.converter.StringHttpMessageConverter();
        stringConverter.setSupportedMediaTypes(java.util.List.of(org.springframework.http.MediaType.ALL));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(clientRequestFactory(timeoutMs))
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof org.springframework.http.converter.StringHttpMessageConverter);
                    converters.add(0, stringConverter);
                })
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory(long timeoutMs) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutMs);
        factory.setReadTimeout((int) timeoutMs);
        return factory;
    }

    /**
     * Llama a Ollama para generar texto a partir de un prompt.
     * <p>
     * Se lee la respuesta como texto plano y se parsea manualmente con Jackson,
     * porque Ollama responde con {@code Content-Type: application/octet-stream}
     * en lugar de {@code application/json}, lo que hace que la negociación de
     * contenido de RestClient rechace la deserialización automática.
     *
     * @param prompt     el prompt completo a enviar al modelo
     * @param jsonFormat true si se necesita que la respuesta sea un JSON estricto
     * @return el texto generado, o Optional.empty() si Ollama no respondió correctamente
     */
    public Optional<String> generate(String prompt, boolean jsonFormat) {
        try {
            OllamaGenerateRequest request = new OllamaGenerateRequest(model, prompt, false, jsonFormat ? "json" : null);
            String rawBody = restClient.post()
                    .uri("/api/generate")
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (rawBody == null || rawBody.isBlank()) {
                return Optional.empty();
            }

            OllamaGenerateResponse response = objectMapper.readValue(rawBody, OllamaGenerateResponse.class);
            if (response.response() == null || response.response().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(response.response());
        } catch (RestClientException | IllegalStateException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("No se pudo contactar a Ollama ({}). KOI continúa con su lógica de reglas.", ex.getMessage());
            return Optional.empty();
        }
    }
}
