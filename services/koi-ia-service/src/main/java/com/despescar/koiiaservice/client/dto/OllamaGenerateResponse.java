package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaGenerateResponse(String model, String response, boolean done) {
}
