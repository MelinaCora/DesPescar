package com.despescar.packageservice.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PackageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePackageNotFound(PackageNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "PACKAGE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicatePackageException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePackage(DuplicatePackageException ex) {
        return build(HttpStatus.CONFLICT, "PACKAGE_DUPLICADO", ex.getMessage());
    }

    @ExceptionHandler(PackageInactiveException.class)
    public ResponseEntity<ErrorResponse> handleInactivePackage(PackageInactiveException ex) {
        return build(HttpStatus.CONFLICT, "PACKAGE_INACTIVO", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.BAD_GATEWAY, "INTEGRACION_EXTERNA_ERROR", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, status.value(), LocalDateTime.now()));
    }
}
