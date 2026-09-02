package com.despescar.koiiaservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class KoiGlobalExceptionHandler {

    @ExceptionHandler(KoiSessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(KoiSessionNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Session Not Found", ex.getMessage());
    }

    @ExceptionHandler(KoiCatalogUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleCatalog(KoiCatalogUnavailableException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Upstream Service Unavailable", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return build(HttpStatus.BAD_REQUEST, "Validation Error", "El mensaje enviado no es válido.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "KOI tuvo un problema inesperado.");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
