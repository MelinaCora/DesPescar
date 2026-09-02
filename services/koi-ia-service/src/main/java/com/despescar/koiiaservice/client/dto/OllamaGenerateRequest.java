package com.despescar.koiiaservice.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaGenerateRequest(String model, String prompt, boolean stream, String format) {
}
