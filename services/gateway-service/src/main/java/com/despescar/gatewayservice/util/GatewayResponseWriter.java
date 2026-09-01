package com.despescar.gatewayservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GatewayResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GatewayResponseWriter() {
    }

    public static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(GatewayRequestContext.REQUEST_ID_HEADER, requestId(exchange));
        byte[] body = serializeBody(exchange, status, error, message);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private static byte[] serializeBody(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());
        body.put("method", exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name());
        body.put("requestId", requestId(exchange));

        try {
            return OBJECT_MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return ("{\"status\":" + status.value() + ",\"error\":\"" + error + "\",\"requestId\":\"" + requestId(exchange) + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String requestId(ServerWebExchange exchange) {
        Object requestId = exchange.getAttribute(GatewayRequestContext.REQUEST_ID_ATTRIBUTE);
        if (requestId == null) {
            return "unknown";
        }
        return requestId.toString();
    }
}
