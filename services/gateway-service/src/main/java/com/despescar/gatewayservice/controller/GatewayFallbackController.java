package com.despescar.gatewayservice.controller;

import com.despescar.gatewayservice.util.GatewayRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class GatewayFallbackController {

    @RequestMapping("/fallback/unavailable")
    public ResponseEntity<Map<String, Object>> unavailable(ServerWebExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Service Unavailable");
        body.put("message", "Servicio temporalmente no disponible.");
        body.put("path", exchange.getRequest().getPath().value());
        body.put("method", exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name());
        body.put("requestId", requestId(exchange));

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .header(GatewayRequestContext.REQUEST_ID_HEADER, requestId(exchange))
                .body(body);
    }

    private String requestId(ServerWebExchange exchange) {
        Object requestId = exchange.getAttribute(GatewayRequestContext.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? "unknown" : requestId.toString();
    }
}
