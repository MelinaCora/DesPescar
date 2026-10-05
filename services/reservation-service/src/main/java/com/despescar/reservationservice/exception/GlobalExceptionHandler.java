package com.despescar.reservationservice.exception;

import com.despescar.reservationservice.config.ClockConfig;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ErrorResponse> handleBookingException(BookingException ex) {
        return error(ex.getStatus(), ex.getCodigo(), ex.getMessage());
    }

    /** Cuerpo que no cumple @Valid: 400 VALIDACION (payment-service lo distingue por el código). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacion(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(campo -> "Dato inválido en " + campo.getField() + ".")
                .orElse("El cuerpo del pedido no es válido.");
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleIlegible(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "El cuerpo del pedido no es válido.");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String codigo, String mensaje) {
        return new ResponseEntity<>(new ErrorResponse(codigo, mensaje, LocalDateTime.now(ClockConfig.ZONA)), status);
    }
}
