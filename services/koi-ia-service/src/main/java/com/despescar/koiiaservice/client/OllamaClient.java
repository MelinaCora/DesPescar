package com.despescar.koiiaservice.client;

import com.despescar.koiiaservice.client.dto.OllamaGenerateRequest;
import com.despescar.koiiaservice.client.dto.OllamaGenerateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
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

    public OllamaClient(@Value("${koi.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
                         @Value("${koi.ai.ollama.model:llama3.2}") String model,
                         @Value("${koi.ai.ollama.timeout-ms:8000}") long timeoutMs) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(clientRequestFactory(timeoutMs))
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
     *
     * @param prompt     el prompt completo a enviar al modelo
     * @param jsonFormat true si se necesita que la respuesta sea un JSON estricto
     * @return el texto generado, o Optional.empty() si Ollama no respondió correctamente
     */
    public Optional<String> generate(String prompt, boolean jsonFormat) {
        try {
            OllamaGenerateRequest request = new OllamaGenerateRequest(model, prompt, false, jsonFormat ? "json" : null);
            OllamaGenerateResponse response = restClient.post()
                    .uri("/api/generate")
                    .body(request)
                    .retrieve()
                    .body(OllamaGenerateResponse.class);

            if (response == null || response.response() == null || response.response().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(response.response());
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("No se pudo contactar a Ollama ({}). KOI continúa con su lógica de reglas.", ex.getMessage());
            return Optional.empty();
        }
    }
}
