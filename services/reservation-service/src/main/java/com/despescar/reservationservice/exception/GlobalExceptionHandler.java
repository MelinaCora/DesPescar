package com.despescar.reservationservice.exception;

import com.despescar.reservationservice.config.ClockConfig;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Todos los errores con el formato del servicio: {codigo, mensaje, timestamp} (D24). */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ErrorResponse> handleBookingException(BookingException ex) {
        return error(ex.getStatus(), ex.getCodigo(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacion(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> "Dato inválido en " + e.getField() + ": " + e.getDefaultMessage())
                .orElse("Los datos enviados no son válidos.");
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleCuerpoIlegible(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "El cuerpo del pedido no es válido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parámetro inválido: " + ex.getName());
    }

    /** Otra operación guardó el mismo carrito primero (@Version de Reservation). */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleCarritoModificado(Exception ex) {
        return error(HttpStatus.CONFLICT, "CARRITO_MODIFICADO",
                "Tu carrito cambió mientras guardábamos. Actualizá y probá de nuevo.");
    }

    /** Espera de lock o deadlock en la base: se puede reintentar, no es un error del servidor. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleLockOcupado(PessimisticLockingFailureException ex) {
        log.warn("Lock ocupado o deadlock: {}", ex.getClass().getSimpleName());
        return error(HttpStatus.CONFLICT, "OPERACION_EN_CURSO",
                "Hay otra operación en curso sobre tu reserva. Probá de nuevo en unos segundos.");
    }

    /** Nunca se devuelve el detalle SQL (claves, tablas): solo al log. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad", ex);
        return error(HttpStatus.CONFLICT, "CONFLICTO", "La operación entra en conflicto con el estado actual.");
    }

    /**
     * Rechazos de seguridad que llegan desde el controlador (@PreAuthorize): se responden acá con el
     * mismo cuerpo que SecurityConfig, sin pasar por el manejador genérico ni dejar un WARN por cada
     * acceso denegado. Un anónimo recibe 401, como en el filtro de Spring Security.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleSeguridad(RuntimeException ex) {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        boolean anonimo = actual == null || actual instanceof AnonymousAuthenticationToken || !actual.isAuthenticated();
        if (ex instanceof AuthenticationException || anonimo) {
            return error(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Necesitás iniciar sesión.");
        }
        return error(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tenés permisos para esta acción.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInesperado(Exception ex) {
        // 404, 405, 415...: Spring MVC ya sabe su código; no es un 500
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(cuerpo(codigoHttp(status), mensajeHttp(status)));
        }
        log.error("Error inesperado", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado.");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatusCode status, String codigo, String mensaje) {
        return ResponseEntity.status(status).body(cuerpo(codigo, mensaje));
    }

    private static ErrorResponse cuerpo(String codigo, String mensaje) {
        return new ErrorResponse(codigo, mensaje, LocalDateTime.now(ClockConfig.ZONA));
    }

    private static String codigoHttp(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "RECURSO_NO_ENCONTRADO";
            case 405 -> "METODO_NO_PERMITIDO";
            case 415 -> "TIPO_NO_SOPORTADO";
            default -> "SOLICITUD_INVALIDA";
        };
    }

    private static String mensajeHttp(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "El recurso no existe.";
            case 405 -> "Método no permitido para este recurso.";
            case 415 -> "Tipo de contenido no soportado.";
            default -> "No se pudo procesar el pedido.";
        };
    }
}
